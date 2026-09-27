use reqwest::{Client, redirect::Policy};
use std::collections::HashMap;
use std::error::Error;
use std::time::{SystemTime, UNIX_EPOCH};

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    // 1. Target Endpoints
    // You will need to find the exact IP or hostname your college uses.
    let login_url = format!("http://192.168.100.1:8090/login.xml");
    
    let username = "24it3004"; 
    let password = "as";

    // 2. Build the client
    // Cyberoam often uses self-signed certs on local networks, so we allow invalid certs for now.
    let client = Client::builder()
        .cookie_store(true)
        .danger_accept_invalid_certs(true) // Required if the college IP uses HTTPS without a valid cert
        .redirect(Policy::none()) // We want to see exactly what the server returns, not follow redirects blindly
        .build()?;

    // 3. Generate the required parameters from the JS file
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis()
        .to_string();

    let mut form_data = HashMap::new();
    form_data.insert("mode", "191".to_string());
    form_data.insert("username", username.to_string());
    form_data.insert("password", password.to_string());
    form_data.insert("a", timestamp);
    form_data.insert("producttype", "0".to_string()); 

    println!("Sending Cyberoam authentication request...");

    // 4. Submit the POST request
    let response = client
        .post(&login_url)
        .form(&form_data)
        .send()
        .await?;

    let status = response.status();
    let response_xml = response.text().await?;

    println!("HTTP Status: {}", status);
    println!("Response: {}", response_xml);

    // The JS file expects XML back. If it says <status>LIVE</status>, you are logged in.
    if response_xml.contains("LIVE") {
    println!("Authentication Successful!");
    } else {
    println!("Authentication Failed.");
}

    Ok(())
}