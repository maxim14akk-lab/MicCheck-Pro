// miccheck_rs.rs — проверка микрофона с визуализацией сигнала на Rust (cpal + консоль)

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::StreamConfig;
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};
use std::f32;

struct AudioData {
    samples: Vec<f32>,
    peak: f32,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let host = cpal::default_host();
    let device = host.default_input_device().expect("Не найдено устройство ввода");
    let config = device.default_input_config().unwrap();
    let sample_rate = config.sample_rate().0;

    let data = Arc::new(Mutex::new(AudioData {
        samples: Vec::new(),
        peak: 0.0,
    }));
    let running = Arc::new(AtomicBool::new(true));

    let data_clone = data.clone();
    let running_clone = running.clone();

    let stream = device.build_input_stream(
        &config.into(),
        move |input: &[f32], _: &cpal::InputCallbackInfo| {
            let mut guard = data_clone.lock().unwrap();
            for &sample in input {
                guard.samples.push(sample);
                if guard.samples.len() > 500 {
                    guard.samples.remove(0);
                }
                if sample.abs() > guard.peak {
                    guard.peak = sample.abs();
                }
            }
        },
        move |err| {
            eprintln!("Ошибка аудио: {}", err);
        },
    )?;
    stream.play()?;

    println!("🎤 MicCheck Pro — Rust Edition");
    println!("Нажмите Ctrl+C для выхода\n");

    let start = Instant::now();
    while running.load(Ordering::SeqCst) {
        std::thread::sleep(Duration::from_millis(50));

        let guard = data.lock().unwrap();
        let samples = &guard.samples;
        let peak = guard.peak;

        let mut rms = 0.0;
        if !samples.is_empty() {
            let sum: f32 = samples.iter().map(|&s| s * s).sum();
            rms = (sum / samples.len() as f32).sqrt();
        }

        let percent = (rms * 200.0).min(100.0) as usize;
        let peak_percent = (peak * 100.0) as usize;
        let status = if percent > 5 { "🟢 Аудио обнаружено" } else { "🔴 Тишина" };

        print!("\rСтатус: {:<20} Громкость: {:3}% Пик: {:3}% ", status, percent, peak_percent);
        let bar_len = 30;
        let filled = percent * bar_len / 100;
        print!("[");
        for i in 0..bar_len {
            if i < filled {
                if percent > 70 {
                    print!("█");
                } else if percent > 40 {
                    print!("▓");
                } else {
                    print!("░");
                }
            } else {
                print!(" ");
            }
        }
        println!("]");
    }
    drop(stream);
    Ok(())
}
