use matrix_sdk::{
    ruma::{events::ignored_user_list::IgnoredUserListEventContent, EventId, UserId},
    Client,
};

use crate::{
    error::{CoreError, Result},
    rooms::known_room,
};

const MAX_REASON_CHARS: usize = 500;

pub async fn block_user(client: &Client, user_id: &str) -> Result<()> {
    let user_id = parse_user(user_id)?;
    client.account().ignore_user(&user_id).await?;
    Ok(())
}

pub async fn unblock_user(client: &Client, user_id: &str) -> Result<()> {
    let user_id = parse_user(user_id)?;
    client.account().unignore_user(&user_id).await?;
    Ok(())
}

pub async fn blocked_users(client: &Client) -> Result<Vec<String>> {
    let content = client
        .account()
        .account_data::<IgnoredUserListEventContent>()
        .await?
        .map(|raw| raw.deserialize())
        .transpose()
        .map_err(|e| CoreError::Sync(e.to_string()))?
        .unwrap_or_default();
    let mut users: Vec<String> = content
        .ignored_users
        .keys()
        .map(ToString::to_string)
        .collect();
    users.sort();
    Ok(users)
}

pub async fn report_message(
    client: &Client,
    room_id: &str,
    event_id: &str,
    reason: Option<&str>,
) -> Result<()> {
    let room = known_room(client, room_id)?;
    let event_id = EventId::parse(event_id.trim())
        .map_err(|e| CoreError::Sync(format!("invalid event id: {e}")))?;
    let reason = reason
        .map(|text| text.trim().chars().take(MAX_REASON_CHARS).collect::<String>())
        .filter(|text| !text.is_empty());
    room.report_content(event_id, reason).await?;
    Ok(())
}

fn parse_user(user_id: &str) -> Result<matrix_sdk::ruma::OwnedUserId> {
    UserId::parse(user_id.trim()).map_err(|e| CoreError::Sync(format!("invalid user id: {e}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn user_ids_are_validated() {
        assert!(parse_user("@alice:example.org").is_ok());
        assert!(parse_user(" @alice:example.org ").is_ok());
        assert!(parse_user("alice").is_err());
    }
}
