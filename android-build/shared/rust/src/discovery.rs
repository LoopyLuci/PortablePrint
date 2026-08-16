/// Discovery module — mDNS service registration and browsing.
use mdns_sd::{ServiceDaemon, ServiceInfo, ServiceEvent};
use std::sync::atomic::{AtomicBool, Ordering};
use std::collections::HashMap;

static DISCOVERY_RUNNING: AtomicBool = AtomicBool::new(false);

/// Register a StreamSync service on the local network via mDNS.
pub fn register_service(hostname: &str, port: u16) -> Result<(), String> {
    let daemon = ServiceDaemon::new().map_err(|e| format!("Daemon error: {}", e))?;
    let mut props = HashMap::new();
    props.insert("device_type".to_string(), "3".to_string());
    props.insert("protocol_version".to_string(), "2".to_string());
    props.insert("capabilities".to_string(), "file_transfer,streaming,clipboard_sync".to_string());

    let service_info = ServiceInfo::new(
        "_streamsync._tcp.local.",
        &format!("StreamSync-{}", hostname),
        &format!("{}.local.", hostname),
        "", // host target (empty = default)
        port,
        Some(props),
    ).map_err(|e| format!("ServiceInfo error: {}", e))?;

    daemon.register(service_info).map_err(|e| format!("Register error: {}", e))?;
    log::info!("mDNS registered: StreamSync-{} on :{}", hostname, port);
    Ok(())
}

/// Browse for StreamSync services on the local network.
pub fn start_browsing() -> Result<(), String> {
    if DISCOVERY_RUNNING.swap(true, Ordering::Relaxed) {
        return Ok(());
    }
    let daemon = ServiceDaemon::new().map_err(|e| format!("Daemon error: {}", e))?;
    let receiver = daemon.browse("_streamsync._tcp.local.")
        .map_err(|e| format!("Browse error: {}", e))?;

    std::thread::spawn(move || {
        while DISCOVERY_RUNNING.load(Ordering::Relaxed) {
            match receiver.recv() {
                Ok(ServiceEvent::ServiceResolved(info)) => {
                    log::info!("Discovered: {} @ {}:{}",
                        info.get_fullname(), info.get_hostname(), info.get_port());
                }
                Ok(ServiceEvent::ServiceRemoved(_, fullname)) => {
                    log::info!("Lost: {}", fullname);
                }
                Ok(_) => {}
                Err(e) => log::error!("Discovery recv error: {}", e),
            }
        }
    });
    log::info!("mDNS browsing started");
    Ok(())
}

/// Stop browsing for services.
pub fn stop_browsing() {
    DISCOVERY_RUNNING.store(false, Ordering::Relaxed);
}

pub fn is_running() -> bool {
    DISCOVERY_RUNNING.load(Ordering::Relaxed)
}
