use pocky_core::{MemberInfo, MemberRole, RoomDetails};

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum Role {
    Admin,
    Moderator,
    Member,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct Member {
    pub user_id: String,
    pub name: Option<String>,
    pub avatar_url: Option<String>,
    pub role: Role,
    pub invited: bool,
    pub is_own: bool,
    pub can_remove: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RoomInfo {
    pub room_id: String,
    pub name: String,
    pub topic: Option<String>,
    pub avatar_url: Option<String>,
    pub is_direct: bool,
    pub is_encrypted: bool,
    pub direct_user_id: Option<String>,
    pub members: Vec<Member>,
    pub can_edit_name: bool,
    pub can_edit_topic: bool,
    pub can_edit_avatar: bool,
    pub can_invite: bool,
}

impl From<MemberInfo> for Member {
    fn from(member: MemberInfo) -> Self {
        Self {
            user_id: member.user_id,
            name: member.name,
            avatar_url: member.avatar_url,
            role: match member.role {
                MemberRole::Admin => Role::Admin,
                MemberRole::Moderator => Role::Moderator,
                MemberRole::Member => Role::Member,
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
            can_edit_name: details.can_edit_name,
            can_edit_topic: details.can_edit_topic,
            can_edit_avatar: details.can_edit_avatar,
            can_invite: details.can_invite,
        }
    }
}
