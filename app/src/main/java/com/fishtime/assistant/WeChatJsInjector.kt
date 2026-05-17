package com.fishtime.assistant

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

/**
 * WeChatJsInjector
 *
 * 原理：
 * 微信在 Root 设备上，X5 WebView 会在 /data/local/tmp/
 * 创建 Unix Domain Socket 用于 Chrome DevTools Protocol（CDP）
 * 我们通过 su 找到这个 socket，发送 CDP 指令注入 JS
 * JS 读取页面号码后写入 localStorage，再通过 CDP 读回来
 */
object WeChatJsInjector {

    private const val TAG = "FishingAssistant"

    // 是否已获得 Root
    var isRootGranted = false
        private set

    // 防止重复注入
    @Volatile
    private var isInjecting = false

    // ────────────────────────────────────────
    // 第一步：检测 Root 权限
    // 在 MainActivity.onCreate() 里调用一次
    // ────────────────────────────────────────

    fun checkRoot(onResult: (Boolean) -> Unit) {
        thread {
            try {
                val p = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "echo root_ok"))
                val result = p.inputStream
                    .bufferedReader()
                    .readLine()
                    ?.trim()
                isRootGranted = (result == "root_ok")
                Log.d(TAG, "Root检测: $isRootGranted")
                onResult(isRootGranted)
            } catch (e: Exception) {
                Log.e(TAG, "Root检测失败: ${e.message}")
                isRootGranted = false
                onResult(false)
            }
        }
    }

    // ────────────────────────────────────────
    // 第二步：找微信 WebView 的 CDP socket
    // 微信 8.x 的 socket 名格式：
    // webview_devtools_remote_<pid>
    // ────────────────────────────────────────

    private fun findCdpSocketName(): String? {
        return try {
            // 通过 su 列出所有 abstract socket，找微信的 devtools
            val cmd = "su -c \"cat /proc/net/unix 2>/dev/null | grep webview_devtools_remote\""
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val output = p.inputStream.bufferedReader().readText()
            Log.d(TAG, "Unix sockets:\n$output")

            // 解析出 socket 名
            // 格式示例：@webview_devtools_remote_12345
            val regex = Regex("webview_devtools_remote_(\\d+)")
            val match = regex.find(output)
            val socketName = match?.value

            Log.d(TAG, "CDP socket: $socketName")
            socketName
        } catch (e: Exception) {
            Log.e(TAG, "查找socket失败: ${e.message}")
            null
        }
    }

    // ────────────────────────────────────────
    // 第三步：通过 socat 转发 Unix Socket 到 TCP
    // 因为 Android Java 无法直接连 abstract socket
    // 用 su + socat 做一个 TCP 桥
    // ────────────────────────────────────────

    private fun forwardSocketToTcp(socketName: String): Boolean {
        return try {
            // 先 kill 旧的 socat（如果有）
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "pkill socat 2>/dev/null"))
                .waitFor()

            Thread.sleep(200)

            // 启动 socat 转发：Unix abstract socket → TCP 9222
            val cmd = "su -c \"socat TCP-LISTEN:9222,fork,reuseaddr ABSTRACT-CONNECT:$socketName &\""
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))

            Thread.sleep(500) // 等 socat 启动

            Log.d(TAG, "socat 转发启动")
            true
        } catch (e: Exception) {
            Log.e(TAG, "socat启动失败: ${e.message}")
            false
        }
    }

    // ────────────────────────────────────────
    // 第四步：通过 CDP HTTP 接口获取页面列表
    // GET http://localhost:9222/json
    // ────────────────────────────────────────

    private fun getPageList(): String? {
        return try {
            val socket = Socket("127.0.0.1", 9222)
            val out: OutputStream = socket.getOutputStream()
            val req = "GET /json HTTP/1.0\r\nHost: localhost\r\n\r\n"
            out.write(req.toByteArray())
            out.flush()

            val reader = BufferedReader(
                InputStreamReader(socket.getInputStream())
            )
            val sb = StringBuilder()
            var line: String?
            var bodyStarted = false
            while (reader.readLine().also { line = it } != null) {
                if (bodyStarted) sb.append(line)
                if (line.isNullOrEmpty()) bodyStarted = true
            }
            socket.close()

            val result = sb.toString()
            Log.d(TAG, "页面列表: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "获取页面列表失败: ${e.message}")
            null
        }
    }

    // ────────────────────────────────────────
    // 第五步：发送 CDP 命令执行 JS
    // 用 WebSocket 协议发送 Runtime.evaluate
    // ────────────────────────────────────────

    private fun executeJs(wsUrl: String, js: String): String? {
        return try {
            // wsUrl 格式: /devtools/page/xxxxx
            // 通过 TCP socket 模拟 WebSocket 握手
            val socket = Socket("127.0.0.1", 9222)
            val out = socket.getOutputStream()
            val input = socket.getInputStream()

            // WebSocket 握手
            val key = "dGhlIHNhbXBsZSBub25jZQ=="
            val handshake = "GET $wsUrl HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"

            out.write(handshake.toByteArray())
            out.flush()

            // 读握手响应（跳过HTTP头）
            val headerBuf = ByteArray(4096)
            val headerLen = input.read(headerBuf)
            val headerStr = String(headerBuf, 0, headerLen)
            Log.d(TAG, "WS握手响应: ${headerStr.take(100)}")

            if (!headerStr.contains("101")) {
                socket.close()
                Log.e(TAG, "WebSocket握手失败")
                return null
            }

            // 构造 CDP Runtime.evaluate 命令
            val cmdObj = JSONObject().apply {
                put("id", 1)
                put("method", "Runtime.evaluate")
                put("params", JSONObject().apply {
                    put("expression", js)
                    put("returnByValue", true)
                })
            }
            val cmdStr = cmdObj.toString()
            val cmdBytes = cmdStr.toByteArray()

            // WebSocket 帧封装（无掩码，服务端→客户端方向）
            // 实际发送需要掩码（客户端→服务端）
            val frame = buildWebSocketFrame(cmdBytes)
            out.write(frame)
            out.flush()

            // 读响应
            Thread.sleep(300)
            val respBuf = ByteArray(8192)
            val respLen = input.read(respBuf)

            socket.close()

            if (respLen <= 0) return null

            // 解析 WebSocket 帧，提取 JSON
            val payload = parseWebSocketFrame(respBuf, respLen)
            Log.d(TAG, "CDP响应: $payload")
            payload

        } catch (e: Exception) {
            Log.e(TAG, "执行JS失败: ${e.message}")
            null
        }
    }

    // WebSocket 客户端帧（带掩码）
    private fun buildWebSocketFrame(payload: ByteArray): ByteArray {
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val len = payload.size
        val frame = mutableListOf<Byte>()

        frame.add(0x81.toByte()) // FIN + text frame

        if (len < 126) {
            frame.add((0x80 or len).toByte()) // MASK bit + length
        } else {
            frame.add((0x80 or 126).toByte())
            frame.add((len shr 8 and 0xFF).toByte())
            frame.add((len and 0xFF).toByte())
        }

        frame.addAll(mask.toList())

        payload.forEachIndexed { i, b ->
            frame.add((b.toInt() xor mask[i % 4].toInt()).toByte())
        }

        return frame.toByteArray()
    }

    // 解析 WebSocket 帧，返回 payload 字符串
    private fun parseWebSocketFrame(buf: ByteArray, len: Int): String? {
        if (len < 2) return null
        val payloadLen = (buf[1].toInt() and 0x7F)
        val start = 2
        if (start + payloadLen > len) return null
        return String(buf, start, payloadLen)
    }

    // ────────────────────────────────────────
    // 对外接口：注入 JS 读取钓位号码
    // 由 FishingAccessibilityService 调用
    // ────────────────────────────────────────

    fun readSeatNumbers(onResult: (seatA: String, seatB: String) -> Unit) {

        if (!isRootGranted) {
            Log.d(TAG, "未获得Root权限")
            return
        }

        if (isInjecting) {
            Log.d(TAG, "正在注入中，跳过")
            return
        }

        isInjecting = true

        thread {
            try {
                // 1. 找 CDP socket
                val socketName = findCdpSocketName()
                if (socketName == null) {
                    Log.d(TAG, "未找到CDP socket，微信WebView未激活")
                    isInjecting = false
                    return@thread
                }

                // 2. socat 转发到 TCP
                val forwarded = forwardSocketToTcp(socketName)
                if (!forwarded) {
                    isInjecting = false
                    return@thread
                }

                // 3. 获取页面列表，找目标页面
                val pageListJson = getPageList()
                if (pageListJson.isNullOrEmpty()) {
                    isInjecting = false
                    return@thread
                }

                // 解析找到包含「钓位」的页面
                val wsPath = parseTargetPage(pageListJson)
                if (wsPath == null) {
                    Log.d(TAG, "未找到钓位页面")
                    isInjecting = false
                    return@thread
                }

                // 4. 注入 JS 读取号码
                val js = """
                    (function(){
                        var title = document.querySelector('#titleText, .title');
                        var titleText = title ? title.innerText : document.body.innerText;
                        if(titleText.indexOf('我的钓位') === -1) return 'NOT_FOUND';
                        
                        var buttons = document.querySelectorAll('button, .seat-btn, [class*="seat"]');
                        var nums = [];
                        buttons.forEach(function(b){
                            var t = b.innerText.trim();
                            var n = parseInt(t);
                            if(!isNaN(n) && n >= 1 && n <= 99) nums.push(t);
                        });
                        
                        if(nums.length >= 2) return nums[0] + ',' + nums[1];
                        
                        var allNums = [];
                        var walker = document.createTreeWalker(
                            document.body,
                            NodeFilter.SHOW_TEXT
                        );
                        while(walker.nextNode()){
                            var v = walker.currentNode.nodeValue.trim();
                            var n = parseInt(v);
                            if(!isNaN(n) && n >= 1 && n <= 99 && v === String(n)){
                                allNums.push(v);
                            }
                        }
                        allNums = [...new Set(allNums)];
                        if(allNums.length >= 2) return allNums[0] + ',' + allNums[1];
                        
                        return 'NO_NUMBERS';
                    })()
                """.trimIndent().replace("\n", " ")

                val response = executeJs(wsPath, js)

                // 5. 解析结果
                if (response != null) {
                    val resultJson = JSONObject(response)
                    val value = resultJson
                        .optJSONObject("result")
                        ?.optJSONObject("result")
                        ?.optString("value") ?: ""

                    Log.d(TAG, "JS返回值: $value")

                    if (value.contains(",")) {
                        val parts = value.split(",")
                        if (parts.size >= 2) {
                            val a = parts[0].trim()
                            val b = parts[1].trim()
                            onResult(a, b)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "注入流程异常: ${e.message}")
            } finally {
                isInjecting = false
                // 清理 socat
                Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "pkill socat 2>/dev/null"))
            }
        }
    }

    // 从页面列表 JSON 中找目标页面的 WebSocket 路径
    private fun parseTargetPage(json: String): String? {
        return try {
            // JSON 格式是数组：[{url, webSocketDebuggerUrl, ...}, ...]
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url", "")
                val wsUrl = obj.optString("webSocketDebuggerUrl", "")
                Log.d(TAG, "页面: $url")
                // 找包含钓场相关域名的页面，或者直接取第一个非空的
                if (wsUrl.isNotEmpty()) {
                    // 提取路径部分
                    val path = wsUrl.replace("ws://localhost:9222", "")
                    return path
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "解析页面列表失败: ${e.message}")
            null
        }
    }
}
