package com.example.photoscore;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class BeatingHeartFullscreenWithText {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new HeartApp().start();
        });
    }

    // 全屏应用入口
    static class HeartApp {
        void start() {
            JFrame frame = new JFrame("Beating Heart Fullscreen with Text & Fireworks");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setUndecorated(true);

            // 进入全屏模式
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            gd.setFullScreenWindow(frame);

            // 使用 LayeredPane 实现心形绘制层和覆盖的退出按钮层
            JLayeredPane layered = new JLayeredPane();
            layered.setPreferredSize(new Dimension(frame.getWidth(), frame.getHeight()));
            frame.setContentPane(layered);

            // 心形层
            HeartPanel heartPanel = new HeartPanel();
            heartPanel.setBounds(0, 0, frame.getWidth(), frame.getHeight());
            layered.add(heartPanel, JLayeredPane.DEFAULT_LAYER);

            // 顶部退出按钮层（覆盖在心形之上）
            final JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
            topPanel.setOpaque(false);
            topPanel.setBounds(0, 0, frame.getWidth(), 60);
            JButton exitBtn = new JButton("Exit");
            exitBtn.addActionListener(e -> {
                frame.dispose();
                System.exit(0);
            });
            topPanel.add(exitBtn);
            layered.add(topPanel, JLayeredPane.PALETTE_LAYER); // 置于覆盖层

            // ESC 退出快捷键
            frame.getRootPane().registerKeyboardAction(e -> {
                frame.dispose();
                System.exit(0);
            }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

            // 自适应布局/尺寸变化
            frame.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    int w = frame.getWidth();
                    int h = frame.getHeight();
                    heartPanel.setBounds(0, 0, w, h);
                    topPanel.setBounds(0, 0, w, 60);
                    layered.revalidate();
                    layered.repaint();
                }
            });

            frame.setVisible(true);
        }
    }

    // 心形绘制、跳动、文本内部显示、以及烟花
    static class HeartPanel extends JPanel {
        private final String innerText = "姚美，我爱你";
        private final Font textFontDefault = new Font("Serif", Font.BOLD, 46);
        private final Color textColor = new Color(255, 105, 180); // 粉色系文本

        private double t = 0.0; // 时间参数，用于驱动跳动和动画
        private final FireworkEngine fw = new FireworkEngine();

        // 背景玫瑰图缓存
        private BufferedImage roseBG = null;

        HeartPanel() {
            // 60 FPS ~ 16ms
            Timer timer = new Timer(16, e -> {
                t += 0.04;
                fw.update(0.016); // dt ~ 16ms
                repaint();
            });
            timer.start();

            // 初始一个心形中心，FireworkEngine 将使用这个中心在绘制时触发爆炸
            fw.setCenter(0, 0);
            setDoubleBuffered(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 背景玫瑰花丛（离屏缓存实现，保持稳定与高性能）
            ensureRoseBackground(w, h);
            if (roseBG != null) {
                g2.drawImage(roseBG, 0, 0, null);
            }

            // 心形的中心位置：在屏幕内来回移动，以“满屏跳动”的效果
            double pad = Math.min(w, h) * 0.08;
            double cx = pad + (w - 2 * pad) * (0.5 + 0.4 * Math.sin(t * 0.7));
            double cy = pad + (h - 2 * pad) * (0.5 + 0.4 * Math.cos(t * 0.6));

            // 心跳缩放（线性、单调变换）
            double baseScale = Math.min(w, h) * 0.028; // 基线缩放，决定心形大小
            double beat = 0.92 + 0.16 * Math.sin(t * 2.0); // 0.76 ~ 1.08 的波动
            double scale = baseScale * beat;

            // 构建心形路径，基于参数方程
            Path2D heart = new Path2D.Double();
            boolean first = true;
            double step = 0.02;
            for (double tt = 0; tt <= 2 * Math.PI + step; tt += step) {
                double x = 16 * Math.pow(Math.sin(tt), 3);
                double y = 13 * Math.cos(tt)
                        - 5 * Math.cos(2 * tt)
                        - 2 * Math.cos(3 * tt)
                        - Math.cos(4 * tt);
                double px = cx + x * scale;
                double py = cy - y * scale;
                if (first) {
                    heart.moveTo(px, py);
                    first = false;
                } else {
                    heart.lineTo(px, py);
                }
            }
            heart.closePath();

            // 1) 心形填充与描边
            g2.setColor(new Color(255, 0, 0, 180));
            g2.fill(heart);
            g2.setColor(Color.RED);
            g2.draw(heart);

            // 2) 在心形内部显示文本
            // 先把画布裁切到心形区域，再在其中绘制文本
            g2.setClip(heart);

            // 动态文本字号，尽量适应心形大小
            int fontSize = Math.max(20, (int) Math.min(Math.min(w, h) * 0.15, 120));
            Font innerFont = new Font("Serif", Font.BOLD, fontSize);
            g2.setFont(innerFont);
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(innerText);
            int textHeight = fm.getHeight();

            double textX = cx - textWidth / 2.0;
            double textY = cy + textHeight / 4.0;

            // 文本轮廓（黑色描边）+ 白色填充，增强可读性
            g2.setColor(Color.BLACK);
            g2.drawString(innerText, (float) (textX - 1), (float) (textY - 1));
            g2.setColor(Color.WHITE);
            g2.drawString(innerText, (float) textX, (float) textY);

            // 还原剪裁
            g2.setClip(null);

            // 3) 更新烟花的中心为当前心形中心，并绘制烟花
            fw.setCenter((int) cx, (int) cy);
            fw.draw(g2);

            g2.dispose();
        }

        // 生成玫瑰花园背景的离屏图片
        private void ensureRoseBackground(int w, int h) {
            if (roseBG != null && roseBG.getWidth() == w && roseBG.getHeight() == h) {
                return;
            }
            roseBG = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = roseBG.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 背景底色与渐变
            GradientPaint gp = new GradientPaint(0, 0, new Color(255, 235, 240),
                    w, h, new Color(255, 180, 210));
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);

            // 玫瑰花丛的简单美化：绘制大量半透明的圆形和花瓣片段，形成花海感
            Random rnd = new Random(1234);
            int roseCount = Math.max(100, Math.min(800, (w * h) / 3000));
            for (int i = 0; i < roseCount; i++) {
                int x = rnd.nextInt(w);
                int y = rnd.nextInt(h);
                int r = 8 + rnd.nextInt(40);
                // 主花朵圆
                Color main = new Color(255, 100 + rnd.nextInt(120), 140, 120);
                g2.setColor(main);
                g2.fill(new Ellipse2D.Double(x - r / 2.0, y - r / 2.0, r, r));

                // 简单花瓣叠加
                g2.setColor(new Color(255, 150, 190, 90));
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3;
                    double px = x + Math.cos(a) * (r * 0.8);
                    double py = y + Math.sin(a) * (r * 0.8);
                    g2.fill(new Ellipse2D.Double(px - r * 0.4, py - r * 0.4, r * 0.8, r * 0.8));
                }
            }

            // 额外的柔和花朵点缀（微背景雾化效果）
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
            for (int i = 0; i < 60; i++) {
                int x = rnd.nextInt(w);
                int y = rnd.nextInt(h);
                int rr = 60 + rnd.nextInt(120);
                g2.setColor(new Color(255, rnd.nextInt(256), rnd.nextInt(256)));
                g2.fill(new Ellipse2D.Double(x - rr / 2.0, y - rr / 2.0, rr, rr));
            }

            g2.dispose();
        }

        // 火焰/烟花相关
        // 继续保留 FireworkEngine 与其内部实现
        // 如果需要在此处继续改造，请在 FireworkEngine 内修改粒子逻辑
        // 心形跳动与文本显示已在上方实现
        // FireworkEngine 已在文件末尾定义
        private void ensureRoseBackgroundUnusedPlaceholder() {
            // 仅为占位，实际实现已在 ensureRoseBackground 中
        }
        // ... 其余代码在文件底部继续（见完整代码）
    }

    // 烟花系统
    static class FireworkEngine {
        private final List<Particle> particles = new ArrayList<>();
        private int centerX = 0;
        private int centerY = 0;
        private final Random rand = new Random();
        private double timer = 0.0;

        void setCenter(int x, int y) {
            this.centerX = x;
            this.centerY = y;
        }

        void update(double dt) {
            timer += dt;
            // 每 0.8 秒触发一次爆炸
            if (timer >= 0.8) {
                spawnExplosion();
                timer = 0.0;
            }
            Iterator<Particle> it = particles.iterator();
            while (it.hasNext()) {
                Particle p = it.next();
                p.update(dt);
                if (!p.alive) {
                    it.remove();
                }
            }
        }

        void draw(Graphics2D g2) {
            for (Particle p : particles) {
                p.draw(g2);
            }
        }

        void spawnExplosion() {
            int n = 120 + rand.nextInt(120); // 增加粒子数量，烟花更大
            double radius = 100 + rand.nextDouble() * 120;
            for (int i = 0; i < n; i++) {
                double a = rand.nextDouble() * 2 * Math.PI;
                double r = radius;
                double sx = centerX + Math.cos(a) * r;
                double sy = centerY + Math.sin(a) * r;

                Particle p = new Particle();
                p.x = sx;
                p.y = sy;
                double speed = 120 + rand.nextDouble() * 420;
                p.vx = Math.cos(a) * speed;
                p.vy = Math.sin(a) * speed;
                p.life = 1.6 + rand.nextDouble() * 1.2; // 更长寿命
                p.color = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
                p.size = 3.0;
                p.alive = true;
                particles.add(p);
            }
        }

        // 粒子
        static class Particle {
            double x, y;
            double vx, vy;
            double life;
            boolean alive = true;
            Color color;
            double size;

            void update(double dt) {
                x += vx * dt;
                y += vy * dt;
                // 简单重力效果
                vy += 180 * dt;
                life -= dt;
                if (life <= 0) alive = false;
            }

            void draw(Graphics2D g2) {
                if (!alive) return;
                g2.setColor(color);
                // 使用一个小圆点表示烟花粒子
                g2.fill(new Ellipse2D.Double(x - size / 2.0, y - size / 2.0, size, size));
            }
        }
    }
}
