use std::{collections::HashMap, sync::LazyLock};

use serde::Deserialize;

const SOURCE: &str = include_str!("../changelog.json");

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Release {
    pub version: String,
    pub version_code: u32,
    pub channel: String,
    pub date: String,
    pub added: Vec<String>,
    pub fixed: Vec<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Entry {
    version: String,
    version_code: u32,
    channel: String,
    date: String,
    new: HashMap<String, Vec<String>>,
    #[serde(default)]
    fixed: HashMap<String, Vec<String>>,
}

static ENTRIES: LazyLock<Vec<Entry>> = LazyLock::new(|| parse(SOURCE));

pub fn changelog(lang: &str, up_to_version_code: u32) -> Vec<Release> {
    releases(&ENTRIES, lang, up_to_version_code)
}

fn parse(source: &str) -> Vec<Entry> {
    let mut entries: Vec<Entry> = serde_json::from_str(source).unwrap_or_default();
    entries.sort_by_key(|entry| std::cmp::Reverse(entry.version_code));
    entries
}

fn releases(entries: &[Entry], lang: &str, up_to_version_code: u32) -> Vec<Release> {
    entries
        .iter()
        .filter(|entry| entry.version_code <= up_to_version_code)
        .map(|entry| Release {
            version: entry.version.clone(),
            version_code: entry.version_code,
            channel: entry.channel.clone(),
            date: entry.date.clone(),
            added: localized(&entry.new, lang),
            fixed: localized(&entry.fixed, lang),
        })
        .collect()
}

fn localized(texts: &HashMap<String, Vec<String>>, lang: &str) -> Vec<String> {
    texts
        .get(lang)
        .or_else(|| texts.get("en"))
        .cloned()
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: &str = r#"[
        {
            "id": "0.9.4", "version": "0.9.4", "versionCode": 904, "channel": "BETA",
            "date": "2026-10-09",
            "new": { "cs": ["Mapa"], "en": ["Map"] }
        },
        {
            "id": "0.9.5", "version": "0.9.5", "versionCode": 905, "channel": "BETA",
            "date": "2026-10-20",
            "new": { "cs": ["Historie změn"], "en": ["Change history"] },
            "fixed": { "cs": ["Oprava stavu"], "en": ["Status fix"] }
        }
    ]"#;

    #[test]
    fn bundled_changelog_is_valid() {
        assert!(!ENTRIES.is_empty());
        assert!(ENTRIES.iter().all(|entry| !entry.new.is_empty()));
    }

    #[test]
    fn newest_first_and_hides_future_versions() {
        let entries = parse(SAMPLE);
        let all = releases(&entries, "cs", 905);
        assert_eq!(all[0].version, "0.9.5");
        assert_eq!(all[0].added, ["Historie změn"]);
        assert_eq!(all[0].fixed, ["Oprava stavu"]);
        assert!(all[1].fixed.is_empty());

        let installed = releases(&entries, "cs", 904);
        assert_eq!(installed.len(), 1);
        assert_eq!(installed[0].version, "0.9.4");
    }

    #[test]
    fn falls_back_to_english() {
        let entries = parse(SAMPLE);
        assert_eq!(releases(&entries, "de", 904)[0].added, ["Map"]);
    }
}
