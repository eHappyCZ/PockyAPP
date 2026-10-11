use pocky_core::{InviteInfo, Person};

#[derive(Debug, Clone, uniffi::Record)]
pub struct Contact {
    pub user_id: String,
    pub display_name: Option<String>,
    pub avatar_url: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct Invitation {
    pub room_id: String,
    pub inviter_id: String,
    pub inviter_name: Option<String>,
    pub inviter_avatar: Option<String>,
}

impl From<Person> for Contact {
    fn from(person: Person) -> Self {
        Self {
            user_id: person.user_id,
            display_name: person.display_name,
            avatar_url: person.avatar_url,
        }
    }
}

impl From<InviteInfo> for Invitation {
    fn from(info: InviteInfo) -> Self {
        Self {
            room_id: info.room_id,
            inviter_id: info.inviter_id,
            inviter_name: info.inviter_name,
            inviter_avatar: info.inviter_avatar,
        }
    }
}
