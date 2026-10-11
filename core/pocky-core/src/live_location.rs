use std::{collections::HashMap, time::Duration};

use eyeball_im::VectorDiff;
use futures_util::{stream::SelectAll, StreamExt};
use imbl::Vector;
use matrix_sdk::{
    live_locations_observer::{LiveLocationShare, LiveLocationsObserver},
    ruma::{events::beacon_info::BeaconInfoEventContent, MilliSecondsSinceUnixEpoch, OwnedRoomId},
    sleep::sleep,
    Client, Room,
};

use crate::rooms::member_name;

const RESCAN: Duration = Duration::from_secs(30);

#[derive(Debug, Clone, PartialEq)]
pub struct LiveShare {
    pub room_id: String,
    pub user_id: String,
    pub name: Option<String>,
    pub avatar_url: Option<String>,
    pub geo_uri: Option<String>,
    pub updated_ms: Option<u64>,
    pub ends_ms: u64,
    pub is_own: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OwnLiveShare {
    pub room_id: String,
    pub ends_ms: u64,
}

fn now_ms() -> u64 {
    u64::from(MilliSecondsSinceUnixEpoch::now().get())
}

fn ends_ms(info: &BeaconInfoEventContent) -> u64 {
    let start = u64::from(info.ts.get());
    let length = u64::try_from(info.timeout.as_millis()).unwrap_or(u64::MAX);
    start.saturating_add(length)
}

pub async fn own_live_shares(client: &Client) -> Vec<OwnLiveShare> {
    let Some(own) = client.user_id().map(ToOwned::to_owned) else {
        return Vec::new();
    };
    let mut shares = Vec::new();
    for room in client.joined_rooms() {
        let Ok(Some(raw)) = room
            .get_state_event_static_for_key::<BeaconInfoEventContent, _>(&own)
            .await
        else {
            continue;
        };
        let Ok(event) = raw.deserialize() else {
            continue;
        };
        let Some(content) = event
            .as_sync()
            .and_then(|e| e.as_original())
            .map(|e| &e.content)
        else {
            continue;
        };
        if content.is_live() {
            shares.push(OwnLiveShare {
                room_id: room.room_id().to_string(),
                ends_ms: ends_ms(content),
            });
        }
    }
    shares
}

pub async fn watch_live_locations(client: &Client, mut on_update: impl FnMut(Vec<LiveShare>)) {
    let own = client.user_id().map(ToOwned::to_owned);
    let mut observers: HashMap<OwnedRoomId, LiveLocationsObserver> = HashMap::new();
    let mut shares: HashMap<OwnedRoomId, Vector<LiveLocationShare>> = HashMap::new();
    let mut streams = SelectAll::new();
    let mut rescan = Box::pin(sleep(Duration::ZERO));
    let mut last: Option<Vec<LiveShare>> = None;

    loop {
        tokio::select! {
            () = &mut rescan => {
                rescan = Box::pin(sleep(RESCAN));
                let joined = client.joined_rooms();
                observers.retain(|id, _| joined.iter().any(|r| r.room_id() == id));
                shares.retain(|id, _| observers.contains_key(id));
                for room in joined {
                    let id = room.room_id().to_owned();
                    if observers.contains_key(&id) {
                        continue;
                    }
                    let observer = room.live_locations_observer().await;
                    let (values, stream) = observer.subscribe();
                    let key = id.clone();
                    streams.push(stream.map(move |diffs| (key.clone(), diffs)));
                    shares.insert(id.clone(), values);
                    observers.insert(id, observer);
                }
            }
            Some((room_id, diffs)) = streams.next() => {
                let Some(values) = shares.get_mut(&room_id) else { continue };
                for diff in diffs {
                    VectorDiff::apply(diff, values);
                }
            }
        }

        let current = collect(client, &shares, own.as_deref()).await;
        if last.as_ref() != Some(&current) {
            on_update(current.clone());
            last = Some(current);
        }
    }
}

async fn collect(
    client: &Client,
    shares: &HashMap<OwnedRoomId, Vector<LiveLocationShare>>,
    own: Option<&matrix_sdk::ruma::UserId>,
) -> Vec<LiveShare> {
    let mut out = Vec::new();
    for (room_id, list) in shares {
        let Some(room) = client.get_room(room_id) else {
            continue;
        };
        for share in list.iter().filter(|s| s.beacon_info.is_live()) {
            out.push(live_share(&room, share, own).await);
        }
    }
    out.sort_by(|a, b| {
        b.updated_ms
            .cmp(&a.updated_ms)
            .then(a.user_id.cmp(&b.user_id))
    });
    out
}

async fn live_share(
    room: &Room,
    share: &LiveLocationShare,
    own: Option<&matrix_sdk::ruma::UserId>,
) -> LiveShare {
    let member = room.get_member_no_sync(&share.user_id).await.ok().flatten();
    LiveShare {
        room_id: room.room_id().to_string(),
        user_id: share.user_id.to_string(),
        name: member_name(room, &share.user_id).await,
        avatar_url: member.and_then(|m| m.avatar_url().map(ToString::to_string)),
        geo_uri: share.last_location.as_ref().map(|l| l.location.uri.clone()),
        updated_ms: share.last_location.as_ref().map(|l| u64::from(l.ts.get())),
        ends_ms: ends_ms(&share.beacon_info),
        is_own: own == Some(share.user_id.as_ref()),
    }
}

pub fn live_share_expired(share: &LiveShare) -> bool {
    share.ends_ms <= now_ms()
}

#[cfg(test)]
mod tests {
    use super::*;
    use matrix_sdk::ruma::MilliSecondsSinceUnixEpoch;

    #[test]
    fn end_is_start_plus_timeout() {
        let mut info = BeaconInfoEventContent::new(None, Duration::from_secs(900), true, None);
        info.ts = MilliSecondsSinceUnixEpoch(1_000_000u32.into());
        assert_eq!(ends_ms(&info), 1_000_000 + 900_000);
    }

    #[test]
    fn expiry_uses_wall_clock() {
        let share = LiveShare {
            room_id: "!r:x".into(),
            user_id: "@u:x".into(),
            name: None,
            avatar_url: None,
            geo_uri: None,
            updated_ms: None,
            ends_ms: now_ms() - 1,
            is_own: false,
        };
        assert!(live_share_expired(&share));
        assert!(!live_share_expired(&LiveShare {
            ends_ms: now_ms() + 60_000,
            ..share
        }));
    }
}
