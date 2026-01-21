use std::env;
use std::fs;

use clap::{arg, command};

#[derive(Clone, Copy)]
struct ICacheEntry {
    valid: bool,
    tag: u32,
}

/*
 * Cache Structure:
 *
 * 16 x [[1 bit Valid] [26 bit Tag] [4 bit Index] [32 bit data]] Cache Line
 *
 * No need to store real data in the cache for this simulation.
 *
 * Ref: ICache.scala
 *
 * */
struct InstructionCache {
    entries: Vec<ICacheEntry>,
    hit_count: u32,
    miss_count: u32,
    offset_width: u32,
    index_width: u32,
}

impl InstructionCache {
    fn new(offset_width: u32, index_width: u32) -> Self {
        println!(
            "ICache Config - Offset Width: {}, Index Width: {}",
            offset_width, index_width
        );
        InstructionCache {
            entries: vec![
                ICacheEntry {
                    valid: false,
                    tag: 0
                };
                2i32.pow(index_width) as usize
            ],
            hit_count: 0,
            miss_count: 0,
            offset_width,
            index_width,
        }
    }

    fn fetch_request(&mut self, address: u32) {
        let index = (address >> self.offset_width) & ((1 << self.index_width) - 1);
        let tag = address >> (self.offset_width + self.index_width);

        let cache_entry = &mut self.entries[index as usize];

        if cache_entry.valid && cache_entry.tag == tag {
            self.hit_count += 1;
        } else {
            self.miss_count += 1;
            cache_entry.valid = true;
            cache_entry.tag = tag;
        }
    }
}

fn main() {
    let matches = command!()
        .arg(arg!(-f --file <FILE> "ITrace file path").required(true))
        .arg(arg!( -i --"icache-config" <CONFIG> "ICache Config").required(true))
        .get_matches();

    let file_name = matches.get_one::<String>("file").unwrap();
    let icache_config = matches
        .get_one::<String>("icache-config")
        .unwrap()
        .split(',')
        .map(|x| u32::from_str_radix(x, 10).unwrap())
        .collect::<Vec<u32>>();

    let mut icache = InstructionCache::new(icache_config[0], icache_config[1]);

    let itrace = fs::read_to_string(file_name)
        .expect("Unable to read ITrace file")
        .lines()
        .map(|x| {
            x.split_once(':')
                .expect("Invalid ITrace format")
                .0
                .trim_start_matches("0x")
        })
        .map(|x| u32::from_str_radix(x, 16).unwrap())
        .collect::<Vec<u32>>();

    for entry in itrace {
        icache.fetch_request(entry);
    }

    println!(
        "Total Instruction: {}, hit: {}, miss: {}, miss rate: {:.2}%",
        icache.hit_count + icache.miss_count,
        icache.hit_count,
        icache.miss_count,
        (icache.miss_count as f64 / (icache.hit_count + icache.miss_count) as f64) * 100.0
    );
}
