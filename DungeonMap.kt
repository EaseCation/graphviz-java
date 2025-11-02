package net.easecation.ecrunelegend.challenge.dungeon.model

import net.easecation.ecrunelegend.ECRuneLegend
import net.easecation.ecrunelegend.challenge.dungeon.Room
import net.easecation.ecrunelegend.challenge.dungeon.RoomState
import net.easecation.ecrunelegend.challenge.dungeon.RoomType
import net.easecation.ecrunelegend.challenge.dungeon.visualization.generateReport
import net.easecation.ecrunelegend.challenge.dungeon.visualization.toDotFile
import net.easecation.ecrunelegend.challenge.dungeon.visualization.PlainFormatParser
import net.easecation.ecrunelegend.challenge.dungeon.visualization.MiniMapRenderer
import net.easecation.ecrunelegend.challenge.dungeon.visualization.PlainLayoutInfo
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO

/**
 * 地牢地图类
 * 代表整个地牢地图的图结构，使用无向图来管理房间之间的连接关系
 */
class DungeonMap {

    /**
     * 内存中的地牢 JPG 渲染图。
     * 在 saveVisualization 成功并生成 JPG 后写入，供运行时直接使用，避免磁盘读取。
     */
    @Volatile
    private var inMemoryJpgImage: BufferedImage? = null

    /**
     * 获取内存中的地牢预览图像（可能为 null）。
     */
    fun getInMemoryPreviewImage(): BufferedImage? = inMemoryJpgImage

    /** 存储所有房间的映射表，键为房间 ID，值为房间对象 */
    val rooms: MutableMap<String, Room> = mutableMapOf()

    /** 存储房间连接关系的邻接表，键为房间 ID，值为连接的房间 ID 集合 */
    val connections: MutableMap<String, MutableSet<String>> = mutableMapOf()

    /**
     * 添加房间到地牢地图中
     * @param room 要添加的房间
     */
    fun addRoom(room: Room) {
        if (!rooms.containsKey(room.id)) {
            rooms[room.id] = room
            connections[room.id] = mutableSetOf()
        }
    }

    /**
     * 在两个房间之间添加连接
     * 由于是无向图，连接是双向的
     *
     * @param roomIdA 第一个房间的 ID
     * @param roomIdB 第二个房间的 ID
     */
    fun addConnection(roomIdA: String, roomIdB: String) {
        // 防止自连接
        if (roomIdA == roomIdB) {
            return
        }

        // 确保两个房间都存在
        if (rooms.containsKey(roomIdA) && rooms.containsKey(roomIdB)) {
            connections[roomIdA]?.add(roomIdB)
            connections[roomIdB]?.add(roomIdA)
        }
    }

    /**
     * 移除两个房间之间的连接
     *
     * @param roomIdA 第一个房间的 ID
     * @param roomIdB 第二个房间的 ID
     */
    fun removeConnection(roomIdA: String, roomIdB: String) {
        connections[roomIdA]?.remove(roomIdB)
        connections[roomIdB]?.remove(roomIdA)
    }

    /**
     * 获取指定房间的所有相邻房间
     *
     * @param roomId 房间 ID
     * @return 相邻房间的列表
     */
    fun getNeighbors(roomId: String): List<Room> {
        return connections[roomId]?.mapNotNull { rooms[it] } ?: emptyList()
    }

    /**
     * 获取指定房间的相邻房间 ID 列表
     *
     * @param roomId 房间 ID
     * @return 相邻房间 ID 的列表
     */
    fun getNeighborIds(roomId: String): List<String> {
        return connections[roomId]?.toList() ?: emptyList()
    }

    /**
     * 判断两个房间是否直接相连
     *
     * @param roomIdA 第一个房间的 ID
     * @param roomIdB 第二个房间的 ID
     * @return 如果两个房间直接相连返回 true
     */
    fun areConnected(roomIdA: String, roomIdB: String): Boolean {
        return connections[roomIdA]?.contains(roomIdB) == true
    }

    /**
     * 获取指定房间的连接数量
     *
     * @param roomId 房间 ID
     * @return 连接数量
     */
    fun getConnectionCount(roomId: String): Int {
        return connections[roomId]?.size ?: 0
    }

    /**
     * 根据房间类型获取房间列表
     *
     * @param type 房间类型
     * @return 指定类型的房间列表
     */
    fun getRoomsByType(type: RoomType): List<Room> {
        return rooms.values.filter { it.type == type }
    }

    /**
     * 根据房间状态获取房间列表
     *
     * @param state 房间状态
     * @return 指定状态的房间列表
     */
    fun getRoomsByState(state: RoomState): List<Room> {
        return rooms.values.filter { it.state == state }
    }

    /**
     * 获取起始房间
     *
     * @return 起始房间，如果不存在返回 null
     */
    fun getStartRoom(): Room? {
        return rooms.values.find { it.type == RoomType.START }
    }

    /**
     * 获取 Boss 房间
     * @return Boss 房间，如果不存在返回 null
     */
    fun getBossRoom(): Room? {
        return rooms.values.find { it.type == RoomType.BOSS }
    }

    /**
     * 使用广度优先搜索计算两个房间之间的最短距离
     *
     * @param fromRoomId 起始房间 ID
     * @param toRoomId 目标房间 ID
     * @return 最短距离，如果无法到达返回 -1
     */
    fun getShortestDistance(fromRoomId: String, toRoomId: String): Int {
        if (fromRoomId == toRoomId) return 0
        if (!rooms.containsKey(fromRoomId) || !rooms.containsKey(toRoomId)) return -1

        val visited = mutableSetOf<String>()
        val queue: Queue<Pair<String, Int>> = LinkedList()
        queue.offer(fromRoomId to 0)
        visited.add(fromRoomId)

        while (queue.isNotEmpty()) {
            val (currentRoomId, distance) = queue.poll()

            for (neighborId in getNeighborIds(currentRoomId)) {
                if (neighborId == toRoomId) {
                    return distance + 1
                }

                if (neighborId !in visited) {
                    visited.add(neighborId)
                    queue.offer(neighborId to distance + 1)
                }
            }
        }

        return -1 // 无法到达
    }

    /**
     * 寻找距离指定房间最远的房间
     *
     * @param fromRoomId 起始房间 ID
     * @return 最远房间的 ID 和距离的配对，如果地图为空返回 null
     */
    fun getFarthestRoom(fromRoomId: String): Pair<String, Int>? {
        if (!rooms.containsKey(fromRoomId)) return null

        val distances = mutableMapOf<String, Int>()
        val visited = mutableSetOf<String>()
        val queue: Queue<Pair<String, Int>> = LinkedList()

        queue.offer(fromRoomId to 0)
        visited.add(fromRoomId)
        distances[fromRoomId] = 0

        while (queue.isNotEmpty()) {
            val (currentRoomId, distance) = queue.poll()

            for (neighborId in getNeighborIds(currentRoomId)) {
                if (neighborId !in visited) {
                    visited.add(neighborId)
                    val newDistance = distance + 1
                    distances[neighborId] = newDistance
                    queue.offer(neighborId to newDistance)
                }
            }
        }

        return distances.maxByOrNull { it.value }?.let { it.key to it.value }
    }

    /**
     * 验证地牢地图的连通性
     *
     * @return 如果所有房间都连通返回 true
     */
    fun isConnected(): Boolean {
        if (rooms.isEmpty()) return true

        val startRoomId = rooms.keys.first()
        val visited = mutableSetOf<String>()
        val stack = mutableListOf<String>()

        stack.add(startRoomId)

        while (stack.isNotEmpty()) {
            val currentRoomId = stack.removeAt(stack.size - 1)
            if (currentRoomId !in visited) {
                visited.add(currentRoomId)
                for (neighborId in getNeighborIds(currentRoomId)) {
                    if (neighborId !in visited) {
                        stack.add(neighborId)
                    }
                }
            }
        }

        return visited.size == rooms.size
    }

    /**
     * 获取地牢地图的统计信息
     *
     * @return 包含各种统计数据的映射表
     */
    fun getStatistics(): Map<String, Any> {
        val totalConnections = connections.values.sumOf { it.size } / 2 // 除以2因为是无向图
        val avgConnections = if (rooms.isNotEmpty()) totalConnections.toDouble() / rooms.size else 0.0

        return mapOf(
            "roomCount" to rooms.size,
            "connectionCount" to totalConnections,
            "averageConnectionsPerRoom" to String.format("%.2f", avgConnections),
            "isConnected" to isConnected(),
            "roomsByType" to RoomType.entries.associateWith { type ->
                getRoomsByType(type).size
            }
        )
    }

    /**
     * 清空地牢地图的所有数据
     */
    fun clear() {
        rooms.clear()
        connections.clear()
    }

    /**
     * 保存地牢可视化文件用于调试和分析
     * 
     * 自动生成 Graphviz .dot 文件和详细报告，保存到插件文件夹中。
     * 支持成功和失败两种情况的可视化输出。
     * 
     * @param validationErrors 验证错误列表（空列表表示成功）
     * @param isSuccess 是否为成功生成（默认根据 validationErrors 是否为空判断）
     * @param customPrefix 自定义文件名前缀（可选）
     * @return 生成的文件信息，包含 .dot 文件和报告文件的路径
     */
    fun saveVisualization(
        validationErrors: List<String> = emptyList(), 
        isSuccess: Boolean = validationErrors.isEmpty(),
        customPrefix: String? = null
    ): VisualizationResult {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val pluginDataFolder = ECRuneLegend.getInstance().dataFolder
            val debugFolder = File(pluginDataFolder, "dungeon_debug")
            
            // 确保调试文件夹存在
            if (!debugFolder.exists()) {
                debugFolder.mkdirs()
            }
            
            // 生成文件名前缀
            val filePrefix = customPrefix ?: if (isSuccess) "success_dungeon" else "failed_dungeon"
            
            // 生成 Graphviz .dot 文件
            val dotContent = this.toDotFile()
            val dotFile = File(debugFolder, "${filePrefix}_${timestamp}.dot")
            dotFile.writeText(dotContent)
            
            // 尝试生成 JPG 图片
            val jpgFile = tryConvertToJpg(dotFile, "${filePrefix}_${timestamp}.jpg")

            // 若磁盘 JPG 生成成功，加载到内存并缓存
            if (jpgFile != null && jpgFile.exists()) {
                runCatching {
                    val img = ImageIO.read(jpgFile)
                    inMemoryJpgImage = img
                }.onFailure {
                    ECRuneLegend.getInstance().logger.warning("  - 读取 JPG 到内存失败: ${it.message}")
                }
            }
            
            // 尝试生成 128×128 小图
            val miniMapFile = tryGenerateMiniMap(dotFile, "${filePrefix}_${timestamp}_mini_128.png")
            
            // 生成详细报告
            val reportContent = generateDetailedReport(validationErrors, isSuccess, timestamp, jpgFile)
            val reportFile = File(debugFolder, "${filePrefix}_${timestamp}_report.txt")
            reportFile.writeText(reportContent)
            
            // 记录日志信息
            logVisualizationResult(isSuccess, dotFile, reportFile)
            
            return VisualizationResult(
                success = true,
                dotFile = dotFile,
                reportFile = reportFile,
                jpgFile = jpgFile,
                message = "可视化文件生成成功"
            )
            
        } catch (e: Exception) {
            ECRuneLegend.getInstance().logger.error("保存地牢可视化文件时出错: ${e.message}")
            e.printStackTrace()
            
            return VisualizationResult(
                success = false,
                dotFile = null,
                reportFile = null,
                jpgFile = null,
                message = "可视化文件生成失败: ${e.message}"
            )
        }
    }

    /**
     * 生成详细的地牢报告
     */
    private fun generateDetailedReport(validationErrors: List<String>, isSuccess: Boolean, timestamp: String, jpgFile: File?): String {
        val reportTitle = if (isSuccess) "地牢生成成功报告" else "地牢生成验证失败报告"
        
        return buildString {
            appendLine(reportTitle)
            appendLine("生成时间: $timestamp")
            appendLine("=".repeat(n = 50))
            appendLine()
            
            // 验证错误信息（仅失败情况）
            if (!isSuccess && validationErrors.isNotEmpty()) {
                appendLine("验证错误:")
                validationErrors.forEach { error ->
                    appendLine("  - $error")
                }
                appendLine()
            }
            
            // 基础统计信息
            appendLine("地牢统计:")
            appendLine("  - 总房间数: ${rooms.size}")
            appendLine("  - 总连接数: ${connections.values.sumOf { it.size } / 2}")
            
            // 房间类型统计
            val roomTypeStats = rooms.values.groupBy { it.type }
                .mapValues { it.value.size }
                .toSortedMap { a, b -> a.displayName.compareTo(b.displayName) }
            
            appendLine("  - 房间类型统计:")
            roomTypeStats.forEach { (type, count) ->
                appendLine("    - ${type.displayName}: $count 个")
            }
            appendLine()
            
            // 连接详情分析
            appendLine("房间连接详情:")
            rooms.forEach { (roomId, room) ->
                val connectionCount = getConnectionCount(roomId)
                val status = if (connectionCount > 3) " [高连接]" else if (connectionCount == 1) " [端点]" else ""
                appendLine("  - $roomId (${room.type.displayName}): $connectionCount 个连接$status")
            }
            appendLine()
            
            // 连通性分析
            val isConnected = isConnected()
            appendLine("连通性分析:")
            appendLine("  - 图连通性: ${if (isConnected) "是" else "否"}")
            
            if (isConnected) {
                val startRoom = getStartRoom()
                val bossRoom = getBossRoom()
                if (startRoom != null && bossRoom != null) {
                    val distance = getShortestDistance(startRoom.id, bossRoom.id)
                    appendLine("  - 起始到Boss距离: ${if (distance >= 0) "$distance 步" else "无法到达"}")
                }
            }
            appendLine()
            
            // 使用地牢自带的统计报告
            append(this@DungeonMap.generateReport())
            appendLine()
            
            // 可视化文件信息
            appendLine("生成的可视化文件:")
            if (jpgFile?.exists() == true) {
                appendLine("  - JPG 图片: ${jpgFile.name} ✓")
            } else {
                appendLine("  - JPG 图片: 生成失败或 Graphviz 不可用")
            }
            appendLine()
            
            // 可视化说明
            appendLine("可视化文件使用说明:")
            appendLine("  - 要生成 PNG 图片: dot -Tpng 文件名.dot -o 输出名.png")
            appendLine("  - 要生成 SVG 矢量图: dot -Tsvg 文件名.dot -o 输出名.svg")
            appendLine("  - 要生成 PDF: dot -Tpdf 文件名.dot -o 输出名.pdf")
        }
    }

    /**
     * 记录可视化结果日志。
     *
     * @param isSuccess 是否为成功生成
     * @param dotFile 生成的 .dot 文件
     * @param reportFile 生成的报告文件
     */
    private fun logVisualizationResult(isSuccess: Boolean, dotFile: File, reportFile: File) {
        if (isSuccess) {
            ECRuneLegend.getInstance().logger.info("地牢生成成功，可视化文件已保存:")
            ECRuneLegend.getInstance().logger.info("  - DOT 文件: ${dotFile.absolutePath}")
            ECRuneLegend.getInstance().logger.info("  - 报告: ${reportFile.absolutePath}")
        } else {
            ECRuneLegend.getInstance().logger.warning("地牢生成失败，调试文件已保存:")
            ECRuneLegend.getInstance().logger.warning("  - DOT 文件: ${dotFile.absolutePath}")
            ECRuneLegend.getInstance().logger.warning("  - 错误报告: ${reportFile.absolutePath}")
        }
    }

    /**
     * 尝试将 .dot 文件转换为高质量的 1280x1280 JPG 图片
     * 
     * 使用两步转换过程：
     * 1. 用 Graphviz 的 dot 命令生成高质量 PNG
     * 2. 用 Java BufferedImage 精确调整到 1280x1280 并保存为 JPG
     * 
     * @param dotFile .dot 源文件
     * @param jpgFileName 输出的 JPG 文件名
     * @return 生成的 JPG 文件，如果失败返回 null
     */
    private fun tryConvertToJpg(dotFile: File, jpgFileName: String): File? {
        return try {
            val jpgFile = File(dotFile.parent, jpgFileName)
            val tempPngFile = File(dotFile.parent, "${dotFile.nameWithoutExtension}_temp.png")
            
            // 第一步：使用 Graphviz 生成高质量 PNG
            val success = generateHighQualityPng(dotFile, tempPngFile)
            if (!success) {
                ECRuneLegend.getInstance().logger.info("  - Graphviz PNG 生成失败")
                return null
            }
            
            // 第二步：将 PNG 精确调整到 1280x1280 并转换为 JPG
            val finalJpg = resizePngToJpg(tempPngFile, jpgFile)
            
            // 清理临时文件
            try {
                tempPngFile.delete()
            } catch (e: Exception) {
                ECRuneLegend.getInstance().logger.warning("  - 清理临时文件失败: ${e.message}")
            }
            
            if (finalJpg != null) {
                ECRuneLegend.getInstance().logger.info("  - 高质量 JPG 图片 (1280x1280): ${jpgFile.absolutePath}")
                finalJpg
            } else {
                ECRuneLegend.getInstance().logger.info("  - JPG 图片生成失败")
                null
            }
            
        } catch (e: Exception) {
            ECRuneLegend.getInstance().logger.warning("  - 转换 JPG 图片时出错: ${e.message}")
            null
        }
    }
    
    /**
     * 使用 Graphviz 生成高质量 PNG 图片
     */
    private fun generateHighQualityPng(dotFile: File, pngFile: File): Boolean {
        return try {
            // 使用高 DPI 和质量参数
            val processBuilder = ProcessBuilder(
                findGraphvizCommand("dot"),
                "-Tpng",
                "-Gdpi=300",          // 高DPI
                "-Gsize=12.8,12.8!",  // 大尺寸确保质量
                "-Gbgcolor=white",    // 白色背景
                dotFile.absolutePath,
                "-o", pngFile.absolutePath
            )
            processBuilder.directory(dotFile.parentFile)
            inheritEnvironment(processBuilder)
            
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            
            exitCode == 0 && pngFile.exists()
            
        } catch (_: IOException) {
            ECRuneLegend.getInstance().logger.info("  - Graphviz 未安装或不可用")
            false
        } catch (e: Exception) {
            ECRuneLegend.getInstance().logger.warning("  - Graphviz PNG 生成出错: ${e.message}")
            false
        }
    }
    
    /**
     * 将 PNG 图片精确调整到 1280x1280 并转换为高质量 JPG
     */
    private fun resizePngToJpg(pngFile: File, jpgFile: File): File? {
        return try {
            // 读取原始 PNG
            val originalImage = ImageIO.read(pngFile)
            if (originalImage == null) {
                ECRuneLegend.getInstance().logger.warning("  - 无法读取 PNG 文件: ${pngFile.name}")
                return null
            }
            
            // 创建 1280x1280 的高质量图像
            val targetSize = 1280
            val resizedImage = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB)
            val g2d = resizedImage.createGraphics()
            
            // 设置高质量渲染参数
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE)
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
            
            // 填充白色背景
            g2d.color = Color.WHITE
            g2d.fillRect(0, 0, targetSize, targetSize)
            
            // 计算缩放和居中参数
            val originalWidth = originalImage.width
            val originalHeight = originalImage.height
            val scaleX = targetSize.toDouble() / originalWidth
            val scaleY = targetSize.toDouble() / originalHeight
            val scale = minOf(scaleX, scaleY) // 保持宽高比
            
            val scaledWidth = (originalWidth * scale).toInt()
            val scaledHeight = (originalHeight * scale).toInt()
            val x = (targetSize - scaledWidth) / 2
            val y = (targetSize - scaledHeight) / 2
            
            // 绘制调整后的图像
            g2d.drawImage(originalImage, x, y, scaledWidth, scaledHeight, null)
            g2d.dispose()
            
            // 保存为 JPG
            val success = ImageIO.write(resizedImage, "jpg", jpgFile)
            if (success && jpgFile.exists()) {
                jpgFile
            } else {
                ECRuneLegend.getInstance().logger.warning("  - JPG 文件保存失败")
                null
            }
            
        } catch (e: Exception) {
            ECRuneLegend.getInstance().logger.warning("  - 图像处理失败: ${e.message}")
            null
        }
    }

    /**
     * 尝试生成 128×128 迷你地图
     * 
     * 使用 Graphviz plain 格式获取布局坐标，然后进行极简重绘。
     * 如果 Graphviz 不可用，使用简单圆形布局作为回退方案。
     * 
     * @param dotFile .dot 源文件
     * @param miniMapFileName 输出的小图文件名
     * @return 生成的小图文件，如果失败返回 null
     */
    private fun tryGenerateMiniMap(dotFile: File, miniMapFileName: String): File? {
        return try {
            val miniMapFile = File(dotFile.parent, miniMapFileName)
            
            // 首选方案：使用 Graphviz plain 格式
            val layoutInfo = tryGetPlainLayout(dotFile)
            val miniMapImage = if (layoutInfo != null) {
                ECRuneLegend.getInstance().logger.info("  - 使用 Graphviz 布局生成 128×128 小图")
                MiniMapRenderer.renderMiniMap(this, layoutInfo, useTransparentBackground = true)
            } else {
                ECRuneLegend.getInstance().logger.info("  - Graphviz 布局失败，使用简单布局生成 128×128 小图")
                MiniMapRenderer.renderMiniMapWithSimpleLayout(this, useTransparentBackground = true)
            }
            
            // 保存图像
            val success = ImageIO.write(miniMapImage, "png", miniMapFile)
            if (success && miniMapFile.exists()) {
                ECRuneLegend.getInstance().logger.info("  - 128×128 小图: ${miniMapFile.absolutePath}")
                
                // 可选：将小图缓存到内存，优先用于 ItemMap
                inMemoryJpgImage = miniMapImage
                
                miniMapFile
            } else {
                ECRuneLegend.getInstance().logger.warning("  - 128×128 小图保存失败")
                null
            }
            
        } catch (e: Exception) {
            ECRuneLegend.getInstance().logger.warning("  - 生成 128×128 小图时出错: ${e.message}")
            null
        }
    }

    /**
     * 尝试使用 Graphviz 获取 plain 格式布局信息
     */
    private fun tryGetPlainLayout(dotFile: File): PlainLayoutInfo? {
        return try {
            val tempPlainFile = File(dotFile.parent, "${dotFile.nameWithoutExtension}_temp.plain")
            
            // 尝试不同的 Graphviz 布局引擎
            val layoutEngines = listOf("sfdp", "neato", "dot")
            
            for (engine in layoutEngines) {
                val command = listOf(findGraphvizCommand(engine), "-Tplain", dotFile.absolutePath)
                val processBuilder = ProcessBuilder(command)
                processBuilder.redirectOutput(tempPlainFile)
                processBuilder.redirectErrorStream(true)
                inheritEnvironment(processBuilder)
                
                val process = processBuilder.start()
                val exitCode = process.waitFor()
                
                if (exitCode == 0 && tempPlainFile.exists() && tempPlainFile.length() > 0) {
                    val plainContent = tempPlainFile.readText()
                    val layoutInfo = PlainFormatParser.parsePlainFormat(plainContent)
                    
                    // 清理临时文件
                    try {
                        tempPlainFile.delete()
                    } catch (e: Exception) {
                        // 忽略清理错误
                    }
                    
                    if (layoutInfo != null) {
                        ECRuneLegend.getInstance().logger.debug("  - 使用 $engine 引擎获取布局信息成功")
                        return layoutInfo
                    }
                }
            }
            
            // 清理临时文件
            try {
                if (tempPlainFile.exists()) {
                    tempPlainFile.delete()
                }
            } catch (e: Exception) {
                // 忽略清理错误
            }
            
            ECRuneLegend.getInstance().logger.debug("  - 所有 Graphviz 引擎都失败")
            null
            
        } catch (e: Exception) {
            ECRuneLegend.getInstance().logger.debug("  - 获取 Graphviz 布局信息失败: ${e.message}")
            null
        }
    }

    /**
     * 查找 Graphviz 命令的完整路径
     */
    private fun findGraphvizCommand(command: String): String {
        // 常见的 Graphviz 安装路径
        val commonPaths = listOf(
            "/usr/bin/$command",
            "/usr/local/bin/$command",
            "/opt/homebrew/bin/$command",
            "/opt/local/bin/$command"
        )
        
        // 首先检查常见路径
        for (path in commonPaths) {
            if (File(path).exists() && File(path).canExecute()) {
                return path
            }
        }
        
        // 如果找不到，尝试使用 which 命令查找
        try {
            val whichProcess = ProcessBuilder("which", command).start()
            val exitCode = whichProcess.waitFor()
            if (exitCode == 0) {
                val path = whichProcess.inputStream.bufferedReader().readText().trim()
                if (path.isNotEmpty() && File(path).exists()) {
                    return path
                }
            }
        } catch (_: Exception) {
            // 忽略
        }
        
        // 默认返回命令名称，让系统从 PATH 中查找
        return command
    }
    
    /**
     * 继承环境变量到 ProcessBuilder
     */
    private fun inheritEnvironment(processBuilder: ProcessBuilder) {
        val env = processBuilder.environment()
        
        // 确保包含常见的 PATH 路径
        val currentPath = env["PATH"] ?: ""
        val additionalPaths = listOf(
            "/usr/bin",
            "/usr/local/bin",
            "/opt/homebrew/bin",
            "/opt/local/bin"
        )
        
        val pathSet = currentPath.split(":").toMutableSet()
        pathSet.addAll(additionalPaths)
        
        env["PATH"] = pathSet.joinToString(":")
    }

    /**
     * 可视化结果数据类。
     *
     * @property success 是否成功生成可视化文件
     * @property dotFile 生成的 .dot 文件
     * @property reportFile 生成的报告文件
     * @property jpgFile 生成的 JPG 文件（如果有）
     * @property message 结果消息
     */
    data class VisualizationResult(
        val success: Boolean,
        val dotFile: File?,
        val reportFile: File?,
        val jpgFile: File?,
        val message: String
    )
}