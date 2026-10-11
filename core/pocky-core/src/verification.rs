use std::{collections::HashSet, future::pending, pin::Pin, sync::Mutex, time::Duration};

use futures_util::{Stream, StreamExt};
use matrix_sdk::{
    encryption::verification::{
        SasState, SasVerification, Verification, VerificationRequest, VerificationRequestState,
    },
    ruma::{
        events::{
            key::verification::request::ToDeviceKeyVerificationRequestEvent,
            room::message::{MessageType, OriginalSyncRoomMessageEvent},
        },
        RoomId, UserId,
    },
    sleep::sleep,
    Client,
};
use matrix_sdk_ui::sync_service::SyncService;
use tokio::sync::mpsc;

use crate::error::{CoreError, Result};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum VerificationPhase {
    Requested,
    Incoming,
    Ready,
    Starting,
    Emojis(Vec<SasEmoji>),
    Confirmed,
    Done,
    Cancelled { by_us: bool, reason: String },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SasEmoji {
    pub index: u8,
    pub symbol: String,
    pub description: String,
}

pub struct VerificationFlow {
    request: VerificationRequest,
    sas: Mutex<Option<SasVerification>>,
}

type SasStream = Pin<Box<dyn Stream<Item = SasState> + Send>>;

impl VerificationFlow {
    pub async fn request_self(client: &Client) -> Result<Self> {
        let user_id = client.user_id().ok_or(CoreError::NotLoggedIn)?.to_owned();
        let identity = client
            .encryption()
            .get_user_identity(&user_id)
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))?
            .ok_or_else(|| CoreError::Encryption("account has no cross-signing identity".into()))?;

        let request = identity
            .request_verification()
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))?;

        Ok(Self::new(request))
    }

    pub async fn request_user(client: &Client, user_id: &str) -> Result<Self> {
        let user = UserId::parse(user_id)
            .map_err(|e| CoreError::Encryption(format!("invalid user id: {e}")))?;
        ensure_direct_room(client, &user).await?;
        client
            .encryption()
            .request_user_identity(&user)
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))?;

        let identity = crate::trust::identity(client, user_id)
            .await?
            .ok_or_else(|| CoreError::Encryption("user has no cross-signing identity".into()))?;

        let request = identity
            .request_verification()
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))?;

        Ok(Self::new(request))
    }

    pub fn new(request: VerificationRequest) -> Self {
        Self {
            request,
            sas: Mutex::new(None),
        }
    }

    pub fn flow_id(&self) -> &str {
        self.request.flow_id()
    }

    pub fn is_incoming(&self) -> bool {
        !self.request.we_started()
    }

    pub fn is_self_verification(&self) -> bool {
        self.request.is_self_verification()
    }

    pub fn other_user_id(&self) -> String {
        self.request.other_user_id().to_string()
    }

    pub async fn follow_room(&self, sync: &SyncService, open_room: Option<&str>) {
        let Some(room_id) = self.request.room_id() else {
            return;
        };
        let open = open_room.and_then(|id| RoomId::parse(id).ok());
        let mut rooms = vec![room_id];
        if let Some(open) = open.as_deref().filter(|open| *open != room_id) {
            rooms.push(open);
        }
        sync.room_list_service()
            .set_room_subscriptions(&rooms)
            .await;
    }

    pub async fn accept(&self) -> Result<()> {
        self.request
            .accept()
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))
    }

    pub async fn confirm(&self) -> Result<()> {
        let sas = self.current_sas()?;
        sas.confirm()
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))
    }

    pub async fn mismatch(&self) -> Result<()> {
        let sas = self.current_sas()?;
        sas.mismatch()
            .await
            .map_err(|e| CoreError::Encryption(e.to_string()))
    }

    pub async fn cancel(&self) -> Result<()> {
        let sas = self.sas.lock().unwrap().clone();
        let result = match sas {
            Some(sas) => sas.cancel().await,
            None => self.request.cancel().await,
        };
        result.map_err(|e| CoreError::Encryption(e.to_string()))
    }

    pub async fn watch(&self, mut on_phase: impl FnMut(VerificationPhase)) {
        let mut requests = Box::pin(self.request.changes());
        let mut sas_states: Option<SasStream> = None;

        let initial = self.request.state();
        if let Some(stream) = self.on_request_state(&initial, &mut on_phase).await {
            sas_states = Some(stream);
        }
        if is_final_request(&initial) {
            return;
        }

        loop {
            tokio::select! {
                state = requests.next() => {
                    let Some(state) = state else { break };
                    if let Some(stream) = self.on_request_state(&state, &mut on_phase).await {
                        sas_states = Some(stream);
                    }
                    if is_final_request(&state) {
                        break;
                    }
                }
                state = next_sas(&mut sas_states) => {
                    let Some(state) = state else {
                        sas_states = None;
                        continue;
                    };
                    if matches!(state, SasState::Cancelled(_)) && !self.request.is_cancelled() {
                        *self.sas.lock().unwrap() = None;
                        sas_states = None;
                        if let VerificationRequestState::Transitioned {
                            verification: Verification::SasV1(sas),
                        } = self.request.state()
                        {
                            sas_states = self.attach(sas, &mut on_phase).await;
                        }
                        continue;
                    }
                    let finished = matches!(state, SasState::Done { .. } | SasState::Cancelled(_));
                    if let Some(phase) = sas_phase(&state) {
                        on_phase(phase);
                    }
                    if finished {
                        break;
                    }
                }
            }
        }
    }

    async fn on_request_state(
        &self,
        state: &VerificationRequestState,
        on_phase: &mut impl FnMut(VerificationPhase),
    ) -> Option<SasStream> {
        match state {
            VerificationRequestState::Created { .. } => {
                on_phase(VerificationPhase::Requested);
                None
            }
            VerificationRequestState::Requested { .. } => {
                on_phase(if self.request.we_started() {
                    VerificationPhase::Requested
                } else {
                    VerificationPhase::Incoming
                });
                None
            }
            VerificationRequestState::Ready { .. } => {
                on_phase(VerificationPhase::Ready);
                if !self.request.we_started() {
                    return None;
                }
                sleep(START_DELAY).await;
                if !matches!(self.request.state(), VerificationRequestState::Ready { .. }) {
                    return None;
                }
                match self.request.start_sas().await {
                    Ok(Some(sas)) => self.attach(sas, on_phase).await,
                    Ok(None) => None,
                    Err(e) => {
                        on_phase(VerificationPhase::Cancelled {
                            by_us: true,
                            reason: e.to_string(),
                        });
                        None
                    }
                }
            }
            VerificationRequestState::Transitioned {
                verification: Verification::SasV1(sas),
            } => self.attach(sas.clone(), on_phase).await,
            VerificationRequestState::Transitioned { .. } => None,
            VerificationRequestState::Done => {
                on_phase(VerificationPhase::Done);
                None
            }
            VerificationRequestState::Cancelled(info) => {
                on_phase(VerificationPhase::Cancelled {
                    by_us: info.cancelled_by_us(),
                    reason: info.reason().to_owned(),
                });
                None
            }
        }
    }

    async fn attach(
        &self,
        sas: SasVerification,
        on_phase: &mut impl FnMut(VerificationPhase),
    ) -> Option<SasStream> {
        {
            let mut current = self.sas.lock().unwrap();
            if current.as_ref().is_some_and(|active| {
                !active.is_cancelled() && active.we_started() == sas.we_started()
            }) {
                return None;
            }
            *current = Some(sas.clone());
        }

        if !sas.we_started() {
            if let Err(e) = sas.accept().await {
                on_phase(VerificationPhase::Cancelled {
                    by_us: true,
                    reason: e.to_string(),
                });
                return None;
            }
        }

        if let Some(phase) = sas_phase(&sas.state()) {
            on_phase(phase);
        }
        Some(Box::pin(sas.changes()))
    }

    fn current_sas(&self) -> Result<SasVerification> {
        self.sas
            .lock()
            .unwrap()
            .clone()
            .ok_or_else(|| CoreError::Encryption("emoji comparison has not started".into()))
    }
}

const START_DELAY: Duration = Duration::from_secs(3);
const DIRECT_ROOM_WAIT: Duration = Duration::from_millis(500);
const DIRECT_ROOM_TRIES: u32 = 20;

async fn ensure_direct_room(client: &Client, user: &UserId) -> Result<()> {
    if client.get_dm_room(user).is_some() {
        return Ok(());
    }
    let room = client
        .joined_rooms()
        .into_iter()
        .find(|room| crate::rooms::direct_user(room).as_deref() == Some(user.as_str()))
        .ok_or_else(|| CoreError::Encryption("no direct chat with this user".into()))?;

    client
        .account()
        .mark_as_dm(room.room_id(), &[user.to_owned()])
        .await?;
    for _ in 0..DIRECT_ROOM_TRIES {
        if client.get_dm_room(user).is_some() {
            return Ok(());
        }
        sleep(DIRECT_ROOM_WAIT).await;
    }
    Err(CoreError::Encryption(
        "direct chat is not marked as direct yet".into(),
    ))
}

pub async fn watch_verification_requests(
    client: &Client,
    mut on_request: impl FnMut(VerificationFlow),
) {
    let (tx, mut rx) = mpsc::unbounded_channel();
    let tx_room = tx.clone();
    let handle = client.add_event_handler(
        move |event: ToDeviceKeyVerificationRequestEvent, client: Client| {
            let tx = tx.clone();
            async move {
                if client.user_id() != Some(&*event.sender) {
                    return;
                }
                if let Some(request) = client
                    .encryption()
                    .get_verification_request(&event.sender, &event.content.transaction_id)
                    .await
                {
                    let _ = tx.send(request);
                }
            }
        },
    );
    let _guard = client.event_handler_drop_guard(handle);

    let in_room = tx_room;
    let room_handle =
        client.add_event_handler(move |event: OriginalSyncRoomMessageEvent, client: Client| {
            let tx = in_room.clone();
            async move {
                let MessageType::VerificationRequest(content) = &event.content.msgtype else {
                    return;
                };
                if client.user_id() != Some(&*content.to) {
                    return;
                }
                match client
                    .encryption()
                    .get_verification_request(&event.sender, &event.event_id)
                    .await
                {
                    Some(request) => {
                        let _ = tx.send(request);
                    }
                    None => {
                        let _ = client
                            .encryption()
                            .request_user_identity(&event.sender)
                            .await;
                    }
                }
            }
        });
    let _room_guard = client.event_handler_drop_guard(room_handle);

    let mut seen = HashSet::new();
    while let Some(request) = rx.recv().await {
        if seen.insert(request.flow_id().to_owned()) {
            on_request(VerificationFlow::new(request));
        }
    }
}

async fn next_sas(stream: &mut Option<SasStream>) -> Option<SasState> {
    match stream.as_mut() {
        Some(stream) => stream.next().await,
        None => pending().await,
    }
}

fn is_final_request(state: &VerificationRequestState) -> bool {
    matches!(
        state,
        VerificationRequestState::Done | VerificationRequestState::Cancelled(_)
    )
}

fn sas_phase(state: &SasState) -> Option<VerificationPhase> {
    match state {
        SasState::Created { .. } | SasState::Started { .. } | SasState::Accepted { .. } => {
            Some(VerificationPhase::Starting)
        }
        SasState::KeysExchanged {
            emojis: Some(emojis),
            ..
        } => Some(VerificationPhase::Emojis(
            emojis
                .indices
                .iter()
                .zip(emojis.emojis.iter())
                .map(|(index, emoji)| SasEmoji {
                    index: *index,
                    symbol: emoji.symbol.to_owned(),
                    description: emoji.description.to_owned(),
                })
                .collect(),
        )),
        SasState::KeysExchanged { emojis: None, .. } => Some(VerificationPhase::Starting),
        SasState::Confirmed => Some(VerificationPhase::Confirmed),
        SasState::Done { .. } => Some(VerificationPhase::Done),
        SasState::Cancelled(info) => Some(VerificationPhase::Cancelled {
            by_us: info.cancelled_by_us(),
            reason: info.reason().to_owned(),
        }),
    }
}

#[cfg(test)]
mod tests {
    use matrix_sdk::encryption::verification::{Emoji, EmojiShortAuthString};

    use super::*;

    #[test]
    fn emojis_keep_spec_indices() {
        let emoji = Emoji {
            symbol: "🐶",
            description: "Dog",
        };
        let state = SasState::KeysExchanged {
            emojis: Some(EmojiShortAuthString {
                indices: [0, 1, 2, 3, 4, 5, 63],
                emojis: [
                    emoji.clone(),
                    emoji.clone(),
                    emoji.clone(),
                    emoji.clone(),
                    emoji.clone(),
                    emoji.clone(),
                    emoji,
                ],
            }),
            decimals: (1, 2, 3),
        };

        let Some(VerificationPhase::Emojis(emojis)) = sas_phase(&state) else {
            panic!("expected emojis");
        };
        assert_eq!(emojis.len(), 7);
        assert_eq!(emojis[6].index, 63);
        assert_eq!(emojis[0].symbol, "🐶");
    }

    #[test]
    fn decimals_only_waits() {
        let state = SasState::KeysExchanged {
            emojis: None,
            decimals: (1, 2, 3),
        };

        assert_eq!(sas_phase(&state), Some(VerificationPhase::Starting));
        assert_eq!(
            sas_phase(&SasState::Confirmed),
            Some(VerificationPhase::Confirmed)
        );
    }
}

#[cfg(test)]
mod live {
    use std::{sync::Arc, time::Duration};

    use matrix_sdk::{
        ruma::api::client::uiaa::{AuthData, MatrixUserIdentifier, Password, UserIdentifier},
        Client,
    };
    use tokio::sync::mpsc;

    use super::*;
    use crate::{
        build_client, create_sync_service, login, logout, open_direct_chat, Server, SERVER_NAME,
    };

    const LIMIT: Duration = Duration::from_secs(180);

    struct Account {
        client: Client,
        sync: SyncService,
        _dir: tempfile::TempDir,
    }

    async fn sign_in(user_var: &str, password_var: &str) -> Account {
        let user = std::env::var(user_var).expect(user_var);
        let password = std::env::var(password_var).expect(password_var);
        let dir = tempfile::tempdir().unwrap();
        let client = build_client(Server::Discover(SERVER_NAME), dir.path(), None)
            .await
            .unwrap();
        login(&client, &user, &password, "PockyAPP verification test")
            .await
            .unwrap();
        ensure_identity(&client, &password).await;

        let sync = create_sync_service(&client).await.unwrap();
        sync.start().await;
        Account {
            client,
            sync,
            _dir: dir,
        }
    }

    async fn ensure_identity(client: &Client, password: &str) {
        let encryption = client.encryption();
        encryption.wait_for_e2ee_initialization_tasks().await;
        if let Err(err) = encryption.bootstrap_cross_signing_if_needed(None).await {
            let info = err
                .as_uiaa_response()
                .expect("cross-signing needs password");
            let user_id = client.user_id().unwrap().to_string();
            let mut auth = Password::new(
                UserIdentifier::Matrix(MatrixUserIdentifier::new(user_id)),
                password.to_owned(),
            );
            auth.session = info.session.clone();
            encryption
                .bootstrap_cross_signing(Some(AuthData::Password(auth)))
                .await
                .unwrap();
        }
    }

    async fn shared_room(alice: &Client, bob: &Client) -> String {
        let bob_id = bob.user_id().unwrap().to_string();
        let room_id = open_direct_chat(alice, &bob_id).await.unwrap();
        let room_id = matrix_sdk::ruma::RoomId::parse(room_id).unwrap();
        for _ in 0..60 {
            match bob.get_room(&room_id) {
                Some(room) if room.state() == matrix_sdk::RoomState::Joined => {
                    return room_id.to_string()
                }
                Some(room) if room.state() == matrix_sdk::RoomState::Invited => {
                    let _ = room.join().await;
                }
                _ => {}
            }
            sleep(Duration::from_secs(1)).await;
        }
        panic!("second account never joined {room_id}");
    }

    async fn run(flow: Arc<VerificationFlow>, who: &'static str) -> VerificationPhase {
        let (tx, mut rx) = mpsc::unbounded_channel();
        let watching = flow.clone();
        tokio::spawn(async move {
            watching
                .watch(|phase| {
                    let _ = tx.send(phase);
                })
                .await;
        });

        while let Some(phase) = rx.recv().await {
            eprintln!("[{who}] {phase:?}");
            match phase {
                VerificationPhase::Emojis(_) => flow.confirm().await.unwrap(),
                VerificationPhase::Done | VerificationPhase::Cancelled { .. } => return phase,
                _ => {}
            }
        }
        VerificationPhase::Cancelled {
            by_us: false,
            reason: "watch ended".to_owned(),
        }
    }

    #[tokio::test(flavor = "multi_thread")]
    #[ignore = "needs POCKY_TEST_USER and POCKY_TEST_PASSWORD"]
    async fn live_self_verification() {
        let _ = tracing_subscriber::fmt()
            .with_env_filter(
                tracing_subscriber::EnvFilter::try_from_default_env()
                    .unwrap_or_else(|_| "warn,matrix_sdk_crypto::verification=info".into()),
            )
            .with_test_writer()
            .try_init();

        let old = sign_in("POCKY_TEST_USER", "POCKY_TEST_PASSWORD").await;
        let recovery = std::env::var("POCKY_TEST_RECOVERY")
            .or_else(|_| std::env::var("POCKY_TEST_PASSWORD"))
            .unwrap();
        crate::recover(&old.client, &recovery)
            .await
            .expect("old device could not be verified with the recovery key");
        let new = sign_in("POCKY_TEST_USER", "POCKY_TEST_PASSWORD").await;
        let own = old.client.user_id().unwrap().to_owned();
        let _ = old.client.encryption().request_user_identity(&own).await;
        let _ = new.client.encryption().request_user_identity(&own).await;
        sleep(Duration::from_secs(5)).await;

        let (tx, mut rx) = mpsc::unbounded_channel();
        let watcher = old.client.clone();
        tokio::spawn(async move {
            watch_verification_requests(&watcher, move |flow| {
                let _ = tx.send(flow);
            })
            .await;
        });

        let started = Arc::new(VerificationFlow::request_self(&new.client).await.unwrap());
        let incoming = Arc::new(
            tokio::time::timeout(LIMIT, rx.recv())
                .await
                .expect("request never reached the other device")
                .unwrap(),
        );
        assert!(incoming.is_self_verification());
        incoming.accept().await.unwrap();

        let outcome = tokio::time::timeout(
            LIMIT,
            futures_util::future::join(run(started, "new device"), run(incoming, "old device")),
        )
        .await;

        logout(&new.client).await.unwrap();
        logout(&old.client).await.unwrap();

        let (requester, responder) = outcome.expect("verification timed out");
        assert_eq!(requester, VerificationPhase::Done);
        assert_eq!(responder, VerificationPhase::Done);
    }

    #[tokio::test(flavor = "multi_thread")]
    #[ignore = "needs POCKY_TEST_USER, POCKY_TEST_PASSWORD, POCKY_TEST_USER2 and POCKY_TEST_PASSWORD2"]
    async fn live_user_verification() {
        let _ = tracing_subscriber::fmt()
            .with_env_filter(
                tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| {
                    "warn,matrix_sdk_crypto::verification=debug,matrix_sdk::encryption::verification=debug".into()
                }),
            )
            .with_test_writer()
            .try_init();

        let alice = sign_in("POCKY_TEST_USER", "POCKY_TEST_PASSWORD").await;
        let bob = sign_in("POCKY_TEST_USER2", "POCKY_TEST_PASSWORD2").await;
        let room_id = shared_room(&alice.client, &bob.client).await;
        let timeline = crate::open_timeline(&alice.client, Some(&alice.sync), &room_id)
            .await
            .unwrap();
        let timeline = Arc::new(timeline);
        let watched = timeline.clone();
        tokio::spawn(async move {
            crate::watch_timeline(&watched, |_| {}).await;
        });
        let alice_id = alice.client.user_id().unwrap().to_owned();
        let bob_user = bob.client.user_id().unwrap().to_owned();
        let _ = bob
            .client
            .encryption()
            .request_user_identity(&alice_id)
            .await;
        let _ = alice
            .client
            .encryption()
            .request_user_identity(&bob_user)
            .await;
        sleep(Duration::from_secs(5)).await;

        let (tx, mut rx) = mpsc::unbounded_channel();
        let watcher = bob.client.clone();
        tokio::spawn(async move {
            watch_verification_requests(&watcher, move |flow| {
                let _ = tx.send(flow);
            })
            .await;
        });

        let bob_id = bob.client.user_id().unwrap().to_string();
        let started = Arc::new(
            VerificationFlow::request_user(&alice.client, &bob_id)
                .await
                .unwrap(),
        );
        let incoming = Arc::new(
            tokio::time::timeout(LIMIT, rx.recv())
                .await
                .expect("request never reached the second account")
                .unwrap(),
        );
        started.follow_room(&alice.sync, Some(&room_id)).await;
        incoming.follow_room(&bob.sync, None).await;
        incoming.accept().await.unwrap();

        let outcome = tokio::time::timeout(
            LIMIT,
            futures_util::future::join(run(started, "requester"), run(incoming, "responder")),
        )
        .await;

        logout(&alice.client).await.unwrap();
        logout(&bob.client).await.unwrap();

        let (requester, responder) = outcome.expect("verification timed out");
        assert_eq!(requester, VerificationPhase::Done);
        assert_eq!(responder, VerificationPhase::Done);
    }
}
