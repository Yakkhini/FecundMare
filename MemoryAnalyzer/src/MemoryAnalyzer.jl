module MemoryAnalyzer

using SQLite
using DataFrames
using StatsBase
using Plots

# Config
DB_PATH = "out/mtrace.db"
BLOCK_SIZE = 256
BURST_PERCENTILE_CUTOFF = 0.80 # Y轴：截断突发长度
COLD_BLOCK_CUTOFF = 0.10       # X轴：过滤冷块
TARGET_X_RES = 1000            
HEATMAP_CLIM_PERCENTILE = 0.80 # Z轴(颜色)：Top 20% 高亮

function generate_heatmap(db_path::String)
    if !isfile(db_path)
        println("错误: 找不到数据库文件 $db_path")
        return
    end

    # 1. [IO] 读取数据
    println("1. 读取数据库...")
    db = SQLite.DB(db_path)
    query = "SELECT address FROM mtrace ORDER BY inst_count ASC"
    df = DBInterface.execute(db, query) |> DataFrame

    if nrow(df) == 0 println("数据为空"); return end

    # 2. [CPU] 计算连续访问
    println("2. 计算连续访问逻辑...")
    raw_addresses = df.address
    real_block_ids = raw_addresses .>> 8 
    
    consecutive_counts = Vector{Int}(undef, nrow(df))
    if nrow(df) > 0 consecutive_counts[1] = 1 end

    @inbounds for i in 2:nrow(df)
        if real_block_ids[i] == real_block_ids[i-1]
            consecutive_counts[i] = consecutive_counts[i-1] + 1
        else
            consecutive_counts[i] = 1
        end
    end

    # 3. [Filter] 过滤冷块
    println("3. 过滤冷块...")
    block_hit_counts = countmap(real_block_ids)
    all_hits = collect(values(block_hit_counts))
    hit_threshold = quantile(all_hits, COLD_BLOCK_CUTOFF)
    
    hot_blocks_set = Set{Int64}()
    for (blk, hits) in block_hit_counts
        if hits > hit_threshold push!(hot_blocks_set, blk) end
    end
    
    # 4. [Mapping] 坐标紧凑化
    println("4. 地址空间紧凑化...")
    sorted_hot_blocks = sort(collect(hot_blocks_set))
    total_hot_blocks = length(sorted_hot_blocks)
    
    block_map = Dict{Int64, Int32}()
    sizehint!(block_map, total_hot_blocks)
    for (i, bid) in enumerate(sorted_hot_blocks)
        block_map[bid] = i
    end

    valid_mask = [b in hot_blocks_set for b in real_block_ids]
    filtered_block_ids = real_block_ids[valid_mask]
    filtered_counts = consecutive_counts[valid_mask]
    
    filtered_mapped_x = Vector{Int32}(undef, length(filtered_block_ids))
    @inbounds for i in 1:length(filtered_block_ids)
        filtered_mapped_x[i] = block_map[filtered_block_ids[i]]
    end

    # 5. [Stats] 统计 Y 轴上限
    println("5. 统计分位数...")
    y_limit_val = Int(round(quantile(filtered_counts, BURST_PERCENTILE_CUTOFF)))
    y_limit_val = max(y_limit_val, 5)

    # 6. [Matrix] 生成矩阵
    println("6. 生成热力图矩阵...")
    num_x_bins = min(total_hot_blocks, TARGET_X_RES)
    x_edges = range(1, total_hot_blocks + 1, length=num_x_bins+1)
    y_edges = 1:(y_limit_val + 1)

    h = fit(Histogram, (filtered_mapped_x, filtered_counts), (x_edges, y_edges))
    
    matrix_raw = h.weights
    matrix_for_plot = matrix_raw'
    float_matrix = Float64.(matrix_for_plot)
    float_matrix[float_matrix .== 0] .= NaN 
    
    # 计算饱和阈值
    non_zero_weights = matrix_raw[matrix_raw .> 0]
    if isempty(non_zero_weights)
        z_saturation_val = 1.0
    else
        z_saturation_val = quantile(non_zero_weights, HEATMAP_CLIM_PERCENTILE)
    end
    z_saturation_val = max(z_saturation_val, 1.0)
    
    println("   -> 颜色饱和阈值(Top 20%): $z_saturation_val")
    println("   -> 提取高亮格子坐标...")
    
    highlight_indices = findall(x -> x > z_saturation_val, matrix_raw)
    x_mids = midpoints(x_edges)
    y_mids = midpoints(y_edges)
    
    overlay_x = Float64[]
    overlay_y = Float64[]
    
    for idx in highlight_indices
        push!(overlay_x, x_mids[idx[1]])
        push!(overlay_y, y_mids[idx[2]])
    end
    
    println("   -> 共标记 $(length(overlay_x)) 个高热度方格")

    # 7. [Ticks] 坐标轴标签
    tick_locs = range(1, total_hot_blocks, length=10)
    tick_real_blocks = [sorted_hot_blocks[Int(round(idx))] for idx in tick_locs]
    tick_labels = ["0x$(string(b * 256, base=16))" for b in tick_real_blocks]
    plot_tick_locs = range(minimum(x_mids), maximum(x_mids), length=10)

    # 8. [Plot] 渲染
    println("7. 渲染图片...")
    
    # --- 修复位置 START ---
    # 计算百分比字符串，先 round 再 Int
    percent_str = string(Int(round((1 - HEATMAP_CLIM_PERCENTILE) * 100)))
    # --- 修复位置 END ---

    p = heatmap(
        x_mids,
        y_mids,
        float_matrix,
        title = "Memory Heatmap (Top $(percent_str)% Highlighted)",
        xlabel = "Memory Address (Filtered & Compacted)",
        ylabel = "Consecutive Count",
        color = :inferno,
        background_color_inside = :black, 
        clims = (1, z_saturation_val), 
        zscale = :log10,
        xticks = (plot_tick_locs, tick_labels),
        xrotation = 45,
        size = (1920, 1080),
        margins = 15Plots.mm
    )
    
    if !isempty(overlay_x)
        scatter!(p, overlay_x, overlay_y,
            marker = :square,
            color = :white,
            markerstrokecolor = :red,
            markerstrokewidth = 1,
            markersize = 3,
            label = "",
            legend = false
        )
    end

    output_filename = "out/memory_heatmap.png"
    savefig(p, output_filename)
    println("完成！已保存为 $output_filename")
end

function (@main)(ARGS)
  generate_heatmap(DB_PATH)
end

end # module MemoryAnalyzer
