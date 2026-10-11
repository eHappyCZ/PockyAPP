use pocky_core::{InviteInfo, MemberInfo, MemberRole, Person, RoomDetails};
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PersonInfo {
    pub user_id: String,
    pub display_name: Option<String>,
    pub avatar_url: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Invite {
    pub room_id: String,
    pub inviter_id: String,
    pub inviter_name: Option<String>,
    pub inviter_avatar: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Member {
    pub user_id: String,
    pub name: Option<String>,
    pub avatar_url: Option<String>,
    pub role: &'static str,
    pub invited: bool,
    pub is_own: bool,
    pub can_remove: bool,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RoomInfo {
    pub room_id: String,
    pub name: String,
    pub topic: Option<String>,
    pub avatar_url: Option<String>,
    pub is_direct: bool,
    pub is_encrypted: bool,
    pub direct_user_id: Option<String>,
    pub members: Vec<Member>,
}

impl From<Person> for PersonInfo {
    fn from(person: Person) -> Self {
        Self {
            user_id: person.user_id,
            display_name: person.display_name,
            avatar_url: person.avatar_url,
        }
    }
}

impl From<InviteInfo> for Invite {
    fn from(info: InviteInfo) -> Self {
        Self {
            room_id: info.room_id,
            inviter_id: info.inviter_id,
            inviter_name: info.inviter_name,
            inviter_avatar: info.inviter_avatar,
        }
    }
}

impl From<MemberInfo> for Member {
    fn from(member: MemberInfo) -> Self {
        Self {
            user_id: member.user_id,
            name: member.name,
            avatar_url: member.avatar_url,
            role: match member.role {
                MemberRole::Admin => "admin",
                MemberRole::Moderator => "moderator",
                MemberRole::Member => "member",
            },
            invited: member.invited,
            is_own: member.is_own,
            can_remove: member.can_remove,
        }
    }
}

impl From<RoomDetails> for RoomInfo {
    fn from(details: RoomDetails) -> Self {
        Self {
            room_id: details.room_id,
            name: details.name,
            topic: details.topic,
            avatar_url: details.avatar_url,
            is_direct: details.is_direct,
            is_encrypted: details.is_encrypted,
            direct_user_id: details.direct_user_id,
            members: details.members.into_iter().map(Into::into).collect(),
        }
    }
}
