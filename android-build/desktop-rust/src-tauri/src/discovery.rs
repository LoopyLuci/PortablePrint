/// Device discovery placeholder module.
/// Full mDNS/DNS-SD integration using mdns-sd crate would go here.
/// For now, devices are discovered by the Tauri frontend via mock data.

use mdns_sd::{ServiceDaemon, ServiceInfo};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

static DISCOVERY_RUNNING: AtomicBool = AtomicBool::new(false);

pub fn is_discovery_running() -> bool {
    DISCOVERY_RUNNING.load(Ordering::Relaxed)
}

/// Register a StreamSync service via mDNS
pub fn register_service(hostname: &str, port: u16) -> Result<(), String> {
    let daemon = ServiceDaemon::new().map_err(|e| format!("mDNS daemon error: {}", e))?;

    let service_info = ServiceInfo::new(
        "_streamsync._tcp.local.",
        &format!("StreamSync-{}", hostname),
        &format!("{}.local.", hostname),
        port,
        None, // TXT records
    )
    .map_err(|e| format!("ServiceInfo error: {}", e))?;

    daemon
        .register(service_info)
        .map_err(|e| format!("Register error: {}", e))?;

    log::info!("mDNS service registered: StreamSync-{} on port {}", hostname, port);
    Ok(())
}

/// Start browsing for StreamSync services
pub fn start_browsing() -> Result<(), String> {
    if DISCOVERY_RUNNING.load(Ordering::Relaxed) {
        return Ok(());
    }

    let daemon = ServiceDaemon::new().map_err(|e| format!("mDNS daemon error: {}", e))?;

    let receiver = daemon
        .browse("_streamsync._tcp.local.")
        .map_err(|e| format!("Browse error: {}", e))?;

    DISCOVERY_RUNNING.store(true, Ordering::Relaxed);

    std::thread::spawn(move || {
        while DISCOVERY_RUNNING.load(Ordering::Relaxed) {
            if let Ok(event) = receiver.recv() {
                match event {
                    mdns_sd::ServiceEvent::ServiceResolved(info) => {
                        log::info!(
                            "Discovered: {} at {:?}:{}",
                            info.get_fullname(),
                            info.get_hostname(),
                            info.get_port()
                        );
                    }
                    mdns_sd::ServiceEvent::ServiceLost(service_type, fullname) => {
                        log::info!("Lost: {} ({})", fullname, service_type);
                    }
                    _ => {}
                }
            }
        }
    });

    Ok(())
}

pub fn stop_browsing() {
    DISCOVERY_RUNNING.store(false, Ordering::Relaxed);
}
