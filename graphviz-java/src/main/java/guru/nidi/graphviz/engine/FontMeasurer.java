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
    
    // 延迟初始化的字段
    @Nullable
    private static volatile FontRenderContext FONT_RENDER_CONTEXT;
    @Nullable
    private static volatile Font COURIER;
    private static volatile double COURIER_SPACE_WIDTH;
    private static volatile double COURIER_BORDER_WIDTH;
    @Nullable
    private static volatile double[] COURIER_WIDTHS;
    private static volatile boolean initialized = false;
    private static volatile boolean initFailed = false;

    private FontMeasurer() {
    }

    // 懒加载初始化
    private static boolean ensureInitialized() {
        if (initialized) {
            return true;
        }
        if (initFailed) {
            return false;
        }
        
        synchronized (FontMeasurer.class) {
            if (initialized) {
                return true;
            }
            if (initFailed) {
                return false;
            }
            
            try {
                FONT_RENDER_CONTEXT = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)
                        .createGraphics().getFontRenderContext();
                COURIER = new Font("Courier", Font.PLAIN, 10);
                COURIER_SPACE_WIDTH = charWidth(COURIER, ' ');
                COURIER_BORDER_WIDTH = borderWidth(COURIER);
                COURIER_WIDTHS = courierWidths();
                initialized = true;
                return true;
            } catch (Error | Exception e) {
                initFailed = true;
                LOG.log(Level.WARNING, "无法初始化字体度量系统，可能缺少 fontconfig。" +
                        "字体度量功能将被禁用。如需完整功能，请安装 fontconfig 或使用 GraphvizCmdLineEngine。", e);
                return false;
            }
        }
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
        // 如果初始化失败，返回空数组表示跳过字体度量
        if (!ensureInitialized()) {
            return new double[0];
        }
        
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
