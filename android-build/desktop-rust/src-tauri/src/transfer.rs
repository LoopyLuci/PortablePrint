use std::collections::HashMap;
use std::net::SocketAddr;
use tokio::net::TcpListener;
use tokio_tungstenite::accept_async;
use futures_util::{SinkExt, StreamExt};
use tokio_tungstenite::tungstenite::Message;

/// Start a WebSocket server for StreamSync on the given port.
/// Accepts incoming connections and spawns a handler for each.
pub async fn start_server(port: u16) -> Result<(), String> {
    let addr: SocketAddr = format!("0.0.0.0:{}", port)
        .parse()
        .map_err(|e| format!("Invalid addr: {}", e))?;
    let listener = TcpListener::bind(addr)
        .await
        .map_err(|e| format!("Bind error: {}", e))?;
    log::info!("StreamSync server listening on port {}", port);

    tokio::spawn(async move {
        while let Ok((stream, peer)) = listener.accept().await {
            log::info!("New connection from: {}", peer);
            tokio::spawn(handle_connection(stream));
        }
    });

    Ok(())
}

async fn handle_connection(stream: tokio::net::TcpStream) {
    let ws_stream = match accept_async(stream).await {
        Ok(ws) => ws,
        Err(e) => {
            log::error!("WebSocket handshake error: {}", e);
            return;
        }
    };

    let (mut write, mut read) = ws_stream.split();

    while let Some(msg) = read.next().await {
        match msg {
            Ok(Message::Text(text)) => {
                log::debug!("Received: {}", text);
                // Echo back for now
                if let Err(e) = write.send(Message::Text(text)).await {
                    log::error!("Send error: {}", e);
                    break;
                }
            }
            Ok(Message::Binary(data)) => {
                log::debug!("Received binary: {} bytes", data.len());
                if let Err(e) = write.send(Message::Binary(data)).await {
                    log::error!("Send error: {}", e);
                    break;
                }
            }
            Ok(Message::Close(_)) => break,
            Err(e) => {
                log::error!("WebSocket error: {}", e);
                break;
            }
            _ => {}
        }
    }
}

/// Connect to a remote StreamSync WebSocket server
pub async fn connect_to(addr: &str) -> Result<(), String> {
    let url = format!("ws://{}", addr);
    let (ws_stream, _) = tokio_tungstenite::connect_async(&url)
        .await
        .map_err(|e| format!("Connection failed: {}", e))?;

    let (mut write, mut read) = ws_stream.split();

    // Send hello
    let hello = serde_json::json!({
        "pv": 2,
        "s": "desktop-client",
        "p": {
            "t": "discovery",
            "d": {
                "type": "hello",
                "dn": whoami::hostname(),
                "dt": 3
            }
        }
    });

    write
        .send(Message::Text(hello.to_string()))
        .await
        .map_err(|e| format!("Send error: {}", e))?;

    log::info!("Connected to {}", addr);

    // Read loop
    while let Some(msg) = read.next().await {
        match msg {
            Ok(Message::Text(text)) => {
                log::info!("Received: {}", text);
            }
            Ok(Message::Close(_)) => break,
            _ => {}
        }
    }

    Ok(())
}
