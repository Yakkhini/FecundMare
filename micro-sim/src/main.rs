use std::env;
use std::fs;

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
    entries: [ICacheEntry; 16],
    hit_count: u32,
    miss_count: u32,
}

fn main() {
    println!("Hello, micro-sim!");

    let args: Vec<String> = env::args().collect();
    let itrace = fs::read_to_string(&args[1])
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

    let mut icache = InstructionCache {
        entries: [ICacheEntry {
            valid: false,
            tag: 0,
        }; 16],
        hit_count: 0,
        miss_count: 0,
    };

    for entry in itrace {
        let index = (entry >> 2) & 0xF;
        let tag = entry >> 6;

        let cache_entry = &mut icache.entries[index as usize];

        if cache_entry.valid && cache_entry.tag == tag {
            icache.hit_count += 1;
        } else {
            icache.miss_count += 1;
            cache_entry.valid = true;
            cache_entry.tag = tag;
        }
    }

    println!(
        "Total Instruction: {}, hit: {}, miss: {}",
        icache.hit_count + icache.miss_count,
        icache.hit_count,
        icache.miss_count
    );
}
