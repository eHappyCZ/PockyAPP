use matrix_sdk::{
    room::RoomMember,
    ruma::{
        events::{
            room::{member::MembershipState, power_levels::UserPowerLevel},
            StateEventType,
        },
        int, UserId,
    },
    Client, RoomMemberships,
};

use crate::{
    error::{CoreError, Result},
    picture::prepare_avatar,
    rooms::{direct_user, known_room, room_avatar},
};

const MAX_TOPIC_CHARS: usize = 500;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum MemberRole {
    Admin,
    Moderator,
    Member,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MemberInfo {
    pub user_id: String,
    pub name: Option<String>,
    pub avatar_url: Option<String>,
    pub role: MemberRole,
    pub invited: bool,
    pub is_own: bool,
    pub can_remove: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RoomDetails {
    pub room_id: String,
    pub name: String,
    pub topic: Option<String>,
    pub avatar_url: Option<String>,
    pub is_direct: bool,
    pub is_encrypted: bool,
    pub direct_user_id: Option<String>,
    pub members: Vec<MemberInfo>,
    pub can_edit_name: bool,
    pub can_edit_topic: bool,
    pub can_edit_avatar: bool,
    pub can_invite: bool,
}

pub async fn room_details(client: &Client, room_id: &str) -> Result<RoomDetails> {
    let room = known_room(client, room_id)?;
    let own = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
    let levels = room.power_levels_or_default().await;
    let can_state = |kind: StateEventType| levels.user_can_send_state(&own, kind);

    let members = room
        .members(RoomMemberships::JOIN | RoomMemberships::INVITE)
        .await?
        .into_iter()
        .map(|member| {
            member_info(
                &member,
                &own,
                levels.user_can_kick_user(&own, member.user_id()),
            )
        })
        .collect();

    Ok(RoomDetails {
        room_id: room.room_id().to_string(),
        name: room
            .cached_display_name()
            .map(|name| name.to_string())
            .unwrap_or_else(|| room.room_id().to_string()),
        topic: room.topic().filter(|topic| !topic.trim().is_empty()),
        avatar_url: room_avatar(&room),
        is_direct: room.direct_targets_length() > 0 || direct_user(&room).is_some(),
        is_encrypted: room.encryption_state().is_encrypted(),
        direct_user_id: direct_user(&room),
        members: sorted(members),
        can_edit_name: can_state(StateEventType::RoomName),
        can_edit_topic: can_state(StateEventType::RoomTopic),
        can_edit_avatar: can_state(StateEventType::RoomAvatar),
        can_invite: levels.user_can_invite(&own),
    })
}

pub async fn set_room_name(client: &Client, room_id: &str, name: &str) -> Result<()> {
    let name = name.split_whitespace().collect::<Vec<_>>().join(" ");
    if name.is_empty() || name.chars().count() > crate::profile::MAX_NAME_CHARS {
        return Err(CoreError::InvalidName("room name".to_owned()));
    }
    known_room(client, room_id)?.set_name(name).await?;
    Ok(())
}

pub async fn set_room_topic(client: &Client, room_id: &str, topic: &str) -> Result<()> {
    let topic = topic.trim();
    if topic.chars().count() > MAX_TOPIC_CHARS {
        return Err(CoreError::InvalidName("topic too long".to_owned()));
    }
    known_room(client, room_id)?.set_room_topic(topic).await?;
    Ok(())
}

pub async fn set_room_avatar(client: &Client, room_id: &str, bytes: &[u8]) -> Result<()> {
    let room = known_room(client, room_id)?;
    let prepared = prepare_avatar(bytes)?;
    room.upload_avatar(&mime::IMAGE_JPEG, prepared.bytes, None)
        .await?;
    Ok(())
}

pub async fn invite_people(client: &Client, room_id: &str, user_ids: &[String]) -> Result<()> {
    let room = known_room(client, room_id)?;
    for user_id in user_ids {
        let user_id = UserId::parse(user_id.trim())
            .map_err(|e| CoreError::Sync(format!("invalid user id: {e}")))?;
        room.invite_user_by_id(&user_id).await?;
    }
    Ok(())
}

pub async fn remove_member(client: &Client, room_id: &str, user_id: &str) -> Result<()> {
    let room = known_room(client, room_id)?;
    let user_id =
        UserId::parse(user_id).map_err(|e| CoreError::Sync(format!("invalid user id: {e}")))?;
    room.kick_user(&user_id, None).await?;
    Ok(())
}

fn member_info(member: &RoomMember, own: &UserId, can_kick: bool) -> MemberInfo {
    let is_own = member.user_id() == own;
    MemberInfo {
        user_id: member.user_id().to_string(),
        name: member.display_name().map(ToOwned::to_owned),
        avatar_url: member.avatar_url().map(|url| url.to_string()),
        role: role(member.power_level()),
        invited: matches!(member.membership(), MembershipState::Invite),
        is_own,
        can_remove: can_kick && !is_own,
    }
}

fn role(level: UserPowerLevel) -> MemberRole {
    match level {
        UserPowerLevel::Infinite => MemberRole::Admin,
        UserPowerLevel::Int(value) if value >= int!(100) => MemberRole::Admin,
        UserPowerLevel::Int(value) if value >= int!(50) => MemberRole::Moderator,
        _ => MemberRole::Member,
    }
}

fn sorted(mut members: Vec<MemberInfo>) -> Vec<MemberInfo> {
    members.sort_by(|a, b| {
        a.invited
            .cmp(&b.invited)
            .then(a.role.cmp(&b.role))
            .then_with(|| sort_name(a).cmp(&sort_name(b)))
    });
    members
}

fn sort_name(member: &MemberInfo) -> String {
    member
        .name
        .clone()
        .unwrap_or_else(|| member.user_id.trim_start_matches('@').to_owned())
        .to_lowercase()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn member(id: &str, name: &str, role: MemberRole, invited: bool) -> MemberInfo {
        MemberInfo {
            user_id: id.to_owned(),
            name: Some(name.to_owned()),
            avatar_url: None,
            role,
            invited,
            is_own: false,
            can_remove: false,
        }
    }

    #[test]
    fn roles_from_power_levels() {
        assert_eq!(role(UserPowerLevel::Infinite), MemberRole::Admin);
        assert_eq!(role(UserPowerLevel::Int(int!(100))), MemberRole::Admin);
        assert_eq!(role(UserPowerLevel::Int(int!(50))), MemberRole::Moderator);
        assert_eq!(role(UserPowerLevel::Int(int!(0))), MemberRole::Member);
    }

    #[test]
    fn admins_first_invited_last_then_by_name() {
        let list = sorted(vec![
            member("@z", "Zuzana", MemberRole::Member, false),
            member("@i", "Ivan", MemberRole::Member, true),
            member("@a", "adam", MemberRole::Member, false),
            member("@k", "Karel", MemberRole::Admin, false),
        ]);

        let ids: Vec<_> = list.iter().map(|m| m.user_id.as_str()).collect();
        assert_eq!(ids, ["@k", "@a", "@z", "@i"]);
    }
}
