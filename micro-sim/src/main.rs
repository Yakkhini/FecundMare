use std::env;
use std::fs;

use clap::{arg, command};
use rusqlite::Connection;

#[derive(Clone, Copy)]
struct CacheEntry {
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
struct Cache {
    entries: Vec<CacheEntry>,
    hit_count: u32,
    miss_count: u32,
    offset_width: u32,
    index_width: u32,
}

impl Cache {
    fn new(offset_width: u32, index_width: u32) -> Self {
        println!(
            "Cache Config - Offset Width: {}, Index Width: {}",
            offset_width, index_width
        );
        Cache {
            entries: vec![
                CacheEntry {
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

    fn fetch_request(&mut self, address: u32, count: bool) {
        let index = (address >> self.offset_width) & ((1 << self.index_width) - 1);
        let tag = address >> (self.offset_width + self.index_width);

        let cache_entry = &mut self.entries[index as usize];

        if cache_entry.valid && cache_entry.tag == tag {
            self.hit_count = if count {
                self.hit_count + 1
            } else {
                self.hit_count
            };
        } else {
            self.miss_count = if count {
                self.miss_count + 1
            } else {
                self.miss_count
            };
            cache_entry.valid = true;
            cache_entry.tag = tag;
        }
    }
}

fn main() {
    let matches = command!()
        .arg(arg!(-f --file <FILE> "Trace file path").required(true))
        .arg(arg!( -i --"icache-config" <CONFIG> "ICache Config"))
        .arg(arg!( -d --"dcache-config" <CONFIG> "DCache Config"))
        .get_matches();

    let file_name = matches.get_one::<String>("file").unwrap();

    let icache_config: Vec<u32>;
    let dcache_config: Vec<u32>;

    let icache_simulation: bool;
    let dcache_simulation: bool;

    if let Some(x) = matches.get_one::<String>("icache-config") {
        icache_config = x
            .split(',')
            .map(|x| u32::from_str_radix(x, 10).unwrap())
            .collect::<Vec<u32>>();
        icache_simulation = true;
    } else {
        println!("Using default ICache config: offset_width=4, index_width=4");
        icache_config = vec![4, 4];
        icache_simulation = false;
    }

    if let Some(x) = matches.get_one::<String>("dcache-config") {
        dcache_config = x
            .split(',')
            .map(|x| u32::from_str_radix(x, 10).unwrap())
            .collect::<Vec<u32>>();
        dcache_simulation = true;
    } else {
        println!("Using default DCache config: offset_width=4, index_width=4");
        dcache_config = vec![4, 4];
        dcache_simulation = false;
    }

    let mut icache = Cache::new(icache_config[0], icache_config[1]);
    let mut dcache = Cache::new(dcache_config[0], dcache_config[1]);

    if icache_simulation && dcache_simulation {
        panic!(
            "-f/--file can only be one format: itrace (text) or mtrace (SQLite); do not enable both icache and dcache in the same run"
        );
    }

    if icache_simulation {
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
            icache.fetch_request(entry, true);
        }

        println!(
            "Total Instruction: {}, hit: {}, miss: {}, miss rate: {:.2}%",
            icache.hit_count + icache.miss_count,
            icache.hit_count,
            icache.miss_count,
            (icache.miss_count as f64 / (icache.hit_count + icache.miss_count) as f64) * 100.0
        );
    }

    if dcache_simulation {
        let conn = Connection::open(file_name).expect("Unable to open mtrace SQLite database");
        let mut stmt = conn
            .prepare("SELECT type, address FROM mtrace ORDER BY inst_count")
            .expect("Failed to prepare mtrace query");

        let mut rows = stmt.query([]).expect("Failed to execute mtrace query");

        while let Some(row) = rows.next().expect("Failed to fetch mtrace row") {
            let access_type: String = row.get(0).expect("Failed to read mtrace type");
            let address: i64 = row.get(1).expect("Failed to read mtrace address");
            let address = u32::try_from(address).expect("mtrace address out of u32 range");

            // write: count=false, read: count=true
            let count = access_type.eq_ignore_ascii_case("READ");
            dcache.fetch_request(address, count);
        }

        println!(
            "Total DCache Read: {}, hit: {}, miss: {}, miss rate: {:.2}%",
            dcache.hit_count + dcache.miss_count,
            dcache.hit_count,
            dcache.miss_count,
            (dcache.miss_count as f64 / (dcache.hit_count + dcache.miss_count) as f64) * 100.0
        );
    }
}
