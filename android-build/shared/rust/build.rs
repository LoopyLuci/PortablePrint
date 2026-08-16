use std::path::PathBuf;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Use vendored protoc to avoid system dependency
    let protoc = protoc_bin_vendored::protoc_bin_path().expect("vendored protoc not found");
    std::env::set_var("PROTOC", protoc.as_os_str());

    let proto_file = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR")?)
        .parent()
        .unwrap()
        .parent()
        .unwrap()
        .join("protocol")
        .join("streamsync.proto");

    println!("cargo:rerun-if-changed={}", proto_file.display());

    let proto_dir = proto_file.parent().unwrap().to_path_buf();

    prost_build::Config::new()
        .out_dir(std::env::var("OUT_DIR")?)
        .compile_protos(&[proto_file.clone()], &[proto_dir])?;

    Ok(())
}
