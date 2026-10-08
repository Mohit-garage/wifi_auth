use std::time::{SystemTime, UNIX_EPOCH};
use thiserror::Error;

use uniffi;

// Define a custom error type that UniFFI can safely export to Kotlin
#[derive(Debug, Error, uniffi::Error)]
pub enum AuthError {
    #[error("Network request failed")]
    NetworkError,
    #[error("Failed to parse response")]
    ParseError,
    #[error("Internal runtime error")]
    RuntimeError,
    #[error("Authentication failed")]
    AuthenticationFailed,
    #[error("Additional authentication challenge required")]
    ChallengeRequired,
}

// Expose the authenticate function to Kotlin via UniFFI
#[uniffi::export]
pub fn authenticate(url: String, username: String, password: String) -> Result<String, AuthError> {
    // Create a dedicated, controlled single-threaded Tokio runtime for this network call.
    // This completely avoids "Cannot start a runtime from within a runtime" panics.
    let rt = tokio::runtime::Builder::new_current_thread()
        .enable_all() // Enables IO and time drivers required by reqwest/hyper
        .build()
        .map_err(|_| AuthError::RuntimeError)?;

    rt.block_on(async {
        // Use an asynchronous reqwest client inside the controlled runtime
        let client = reqwest::Client::builder()
            .cookie_store(true)
            .timeout(std::time::Duration::from_secs(15))
            .build()
            .map_err(|_| AuthError::NetworkError)?;

        // Cyberoam/Sophos portal contract discovered from its JavaScript:
        // login.xml expects a URL-encoded POST with these fields.
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|_| AuthError::RuntimeError)?
            .as_millis()
            .to_string();
        let params = [
            ("mode", "191".to_string()),
            ("username", username),
            ("password", password),
            ("a", timestamp),
            ("producttype", "2".to_string()),
        ];

        let response = client
            .post(&url)
            .form(&params)
            .send()
            .await
            .map_err(|_| AuthError::NetworkError)?;

        let status = response.status();
        let body = response.text().await.map_err(|_| AuthError::ParseError)?;

        if !status.is_success() {
            return Err(AuthError::NetworkError);
        }

        match xml_tag_value(&body, "status").as_deref() {
            Some("LIVE") => Ok("authenticated".to_string()),
            Some("LOGIN") => Err(AuthError::AuthenticationFailed),
            Some("CHALLENGE") => Err(AuthError::ChallengeRequired),
            _ => Err(AuthError::ParseError),
        }
    })
}

fn xml_tag_value(body: &str, tag: &str) -> Option<String> {
    let start_tag = format!("<{tag}>");
    let end_tag = format!("</{tag}>");
    let start = body.find(&start_tag)? + start_tag.len();
    let end = body[start..].find(&end_tag)? + start;
    Some(body[start..end].trim().to_string())
}

uniffi::setup_scaffolding!();
