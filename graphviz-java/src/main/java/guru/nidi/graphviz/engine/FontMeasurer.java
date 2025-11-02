/*
 * Copyright © 2015 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.graphviz.engine;

import javax.annotation.Nullable;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

final class FontMeasurer {
    private static final Logger LOG = Logger.getLogger(FontMeasurer.class.getName());
    private static final double COURIER_WIDTH = .5999;
    private static final Set<String> FONTS = new HashSet<>();
    
    // 系统属性：是否禁用字体度量（默认不禁用）
    private static final boolean FONT_MEASUREMENT_DISABLED = 
        Boolean.getBoolean("graphviz.font.measurement.disabled");
    
    // 初始化状态枚举
    private enum InitState {
        NOT_STARTED,    // 未开始
        SUCCESS,        // 成功
        FAILED          // 失败
    }
    
    // 使用枚举代替多个 boolean 标志，更清晰
    private static volatile InitState initState = InitState.NOT_STARTED;
    
    // 延迟初始化的字段
    @Nullable
    private static FontRenderContext FONT_RENDER_CONTEXT;
    @Nullable
    private static Font COURIER;
    private static double COURIER_SPACE_WIDTH;
    private static double COURIER_BORDER_WIDTH;
    @Nullable
    private static double[] COURIER_WIDTHS;

    private FontMeasurer() {
    }
    
    /**
     * 静态初始化块：在类加载时就尝试初始化
     * 这样可以在应用启动阶段就发现问题，而不是在运行时
     */
    static {
        if (FONT_MEASUREMENT_DISABLED) {
            initState = InitState.FAILED;
            LOG.info("字体度量功能已通过系统属性禁用 (-Dgraphviz.font.measurement.disabled=true)");
        } else {
            // 在类加载时就尝试初始化，而不是懒加载
            tryInitialize();
        }
    }

    /**
     * 尝试初始化字体系统
     * 1. 在静态初始化块中调用，JVM 保证类加载的线程安全
     * 2. 即使多线程调用，最坏情况是重复初始化，但不会导致阻塞
     */
    private static void tryInitialize() {
        try {
            // 设置 headless 模式，避免 GUI 依赖
            System.setProperty("java.awt.headless", "true");
            
            FONT_RENDER_CONTEXT = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)
                    .createGraphics().getFontRenderContext();
            COURIER = new Font("Courier", Font.PLAIN, 10);
            COURIER_SPACE_WIDTH = charWidth(COURIER, ' ');
            COURIER_BORDER_WIDTH = borderWidth(COURIER);
            COURIER_WIDTHS = courierWidths();
            
            initState = InitState.SUCCESS;
            LOG.info("字体度量系统初始化成功");
            
        } catch (Error | Exception e) {
            initState = InitState.FAILED;
            LOG.log(Level.WARNING, 
                "字体度量系统初始化失败。字体度量功能将被禁用。\n" +
                "可能的原因：\n" +
                "  1. 缺少 fontconfig 库\n" +
                "  2. AWT 环境配置问题\n" +
                "解决方案：\n" +
                "  1. 安装 fontconfig: \n" +
                "     - Debian/Ubuntu: apt-get install fontconfig\n" +
                "     - CentOS/RHEL: yum install fontconfig\n" +
                "  2. 使用 GraphvizCmdLineEngine 代替 JS 引擎\n" +
                "  3. 主动禁用字体度量: -Dgraphviz.font.measurement.disabled=true", 
                e);
        }
    }
    
    /**
     * 检查字体系统是否可用
     */
    private static boolean isAvailable() {
        return initState == InitState.SUCCESS;
    }

    private static double[] courierWidths() {
        double[] w = new double[256];
        for (int i = 32; i < 256; i++) {
            w[i] = charWidth(COURIER, (char) i);
        }
        return w;
    }

    private static double charWidth(Font font, char c) {
        return font.createGlyphVector(FONT_RENDER_CONTEXT, new char[]{56, c, 56}).getVisualBounds().getWidth();
    }

    private static double borderWidth(Font font) {
        return font.createGlyphVector(FONT_RENDER_CONTEXT, new char[]{56, 56}).getVisualBounds().getWidth();
    }

    static double[] measureFont(String name) {
        // 快速失败：如果字体系统不可用，直接返回
        if (!isAvailable()) {
            return new double[0];
        }
        
        // 避免重复度量同一字体
        if (FONTS.contains(name)) {
            return new double[0];
        }
        FONTS.add(name);
        
        try {
            final Font font = new Font(name, Font.PLAIN, 10);
            final double spaceWidth = charWidth(font, ' ');
            final double borderWidth = borderWidth(font);
            double[] w = new double[256];
            for (int i = 0; i < 256; i++) {
                w[i] = COURIER_WIDTH * (i <= 32
                        ? (spaceWidth - borderWidth) / (COURIER_SPACE_WIDTH - COURIER_BORDER_WIDTH)
                        : (charWidth(font, (char) i) - borderWidth) / (COURIER_WIDTHS[i] - COURIER_BORDER_WIDTH));
            }
            return w;
        } catch (Exception e) {
            LOG.log(Level.FINE, "无法度量字体: " + name, e);
            return new double[0];
        }
    }
}
