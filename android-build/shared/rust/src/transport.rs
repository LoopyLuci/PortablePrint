/// Transport module — WebSocket client and server for StreamSync.
use futures_util::{SinkExt, StreamExt};
use std::net::SocketAddr;
use tokio::net::{TcpListener, TcpStream};
use tokio_tungstenite::{accept_async, connect_async, MaybeTlsStream};

type WsStream = tokio_tungstenite::WebSocketStream<MaybeTlsStream<TcpStream>>;

/// Start a WebSocket server for StreamSync.
pub async fn start_server(port: u16) -> Result<(), String> {
    let addr: SocketAddr = format!("0.0.0.0:{}", port)
        .parse()
        .map_err(|e| format!("Addr: {}", e))?;
    let listener = TcpListener::bind(addr).await.map_err(|e| format!("Bind: {}", e))?;
    log::info!("StreamSync server on {}", addr);

    tokio::spawn(async move {
        while let Ok((stream, peer)) = listener.accept().await {
            log::info!("New connection from: {}", peer);
            tokio::spawn(handle_connection(stream));
        }
    });
    Ok(())
}

async fn handle_connection(stream: TcpStream) {
    let ws = match accept_async(stream).await {
        Ok(ws) => ws,
        Err(e) => { log::error!("WS handshake: {}", e); return; }
    };
    let (mut tx, mut rx) = ws.split();
    while let Some(msg) = rx.next().await {
        match msg {
            Ok(tokio_tungstenite::tungstenite::Message::Text(text)) => {
                log::debug!("Text ({}b)", text.len());
                let _ = tx.send(tokio_tungstenite::tungstenite::Message::Text(text)).await;
            }
            Ok(tokio_tungstenite::tungstenite::Message::Binary(data)) => {
                log::debug!("Binary ({}b)", data.len());
                let _ = tx.send(tokio_tungstenite::tungstenite::Message::Binary(data)).await;
            }
            Ok(tokio_tungstenite::tungstenite::Message::Close(_)) => break,
            Err(e) => { log::error!("WS error: {}", e); break; }
            _ => {}
        }
    }
}

/// Connect to a remote StreamSync WebSocket server.
pub async fn connect(addr: &str) -> Result<WsStream, String> {
    let url = format!("ws://{}/streamsync", addr);
    let (ws, _) = connect_async(&url).await.map_err(|e| format!("Connect {}", e))?;
    log::info!("Connected to {}", addr);
    Ok(ws)
}

/// Send protobuf bytes over WebSocket.
pub async fn send_raw(tx: &mut futures_util::stream::SplitSink<WsStream, tokio_tungstenite::tungstenite::Message>, data: &[u8]) -> Result<(), String> {
    tx.send(tokio_tungstenite::tungstenite::Message::Binary(data.to_vec()))
        .await.map_err(|e| format!("Send: {}", e))
}

/// Read next message from WebSocket.
pub async fn recv_raw(rx: &mut futures_util::stream::SplitStream<WsStream>) -> Result<Option<Vec<u8>>, String> {
    match rx.next().await {
        Some(Ok(tokio_tungstenite::tungstenite::Message::Binary(d))) => Ok(Some(d.to_vec())),
        Some(Ok(tokio_tungstenite::tungstenite::Message::Text(t))) => Ok(Some(t.into_bytes())),
        Some(Ok(tokio_tungstenite::tungstenite::Message::Close(_))) => Ok(None),
        Some(Err(e)) => Err(format!("Recv: {}", e)),
        None => Ok(None),
        _ => Ok(None),
    }
}
