import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KayakGame extends JPanel implements ActionListener, KeyListener {
    static final int W = 640, H = 720;
    static final int TICK = 16;

    enum State { MENU, PLAYING, GAME_OVER }

    State state = State.MENU;
    Timer timer;

    // kayak
    double kx = W / 2.0, ky = H - 130;
    double hull = 100, score = 0, dist = 0, time = 0;
    double paddlePhase = 0;
    double hardTimer = 0, spawnTimer = 0;

    final Set<Integer> keys = new HashSet<>();
    final List<Obstacle> obstacles = new ArrayList<>();
    final List<Splash> splashes = new ArrayList<>();
    final List<Particle> particles = new ArrayList<>();

    static class Obstacle {
        final boolean rock;
        double x;
        final double size;        double y, spin;
        final double sway;
        Obstacle(boolean rock, double x, double y, double size) {
            this.rock = rock; this.x = x; this.y = y; this.size = size;
            this.spin = Math.random() * 0.1; this.sway = 0.5 + Math.random() * 0.8;
        }
    }
    static class Splash {
        double x, y, wob;
        boolean taken;
        Splash(double x, double y) { this.x = x; this.y = y; this.wob = Math.random() * 6.28; }
    }
    static class Particle {
        double x, y, vx, vy, life, max;
        Color c;
        Particle(double x, double y, double vx, double vy, Color c, double life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.c = c; this.life = life; this.max = life;
        }
        boolean update(double dt) { x += vx * dt; y += vy * dt; vy += 60 * dt; life -= dt; return life > 0; }
    }

    KayakGame() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        timer = new Timer(TICK, this);
        timer.start();
    }

    double currentSpeed() {
        double s = 2.2 + dist / 2200.0;
        if (keys.contains(KeyEvent.VK_UP)) s *= 1.8;
        if (keys.contains(KeyEvent.VK_DOWN)) s *= 0.35;
        return Math.min(s, 6.5 + dist / 4000.0);
    }

    void reset() {
        kx = W / 2.0; ky = H - 130; hull = 100; score = 0; dist = 0; time = 0;
        hardTimer = 0; spawnTimer = 0; paddlePhase = 0;
        obstacles.clear(); splashes.clear(); particles.clear();
    }

    void startGame() { reset(); state = State.PLAYING; }

    void spawn() {
        double margin = 70;
        double x = margin + Math.random() * (W - margin * 2);
        double r = Math.random();
        if (r < 0.6) {
            obstacles.add(new Obstacle(true, x, -30, 22 + Math.random() * 22));
        } else if (r < 0.9) {
            obstacles.add(new Obstacle(false, x, -30, 30 + Math.random() * 26));
        } else {
            splashes.add(new Splash(x, -30));
        }
    }

    void hurt(double amount) {
        hull -= amount;
        for (int i = 0; i < 20; i++) {
            double a = Math.random() * Math.PI * 2;
            particles.add(new Particle(kx, ky, Math.cos(a) * 60, Math.sin(a) * 60 - 40, new Color(255, 200, 90), 0.6));
        }
        if (hull <= 0) { hull = 0; state = State.GAME_OVER; }
    }

    boolean collide(double ox, double oy, double radius) {
        double hw = 23, hh = 45;
        double cx = Math.max(kx - hw, Math.min(ox, kx + hw));
        double cy = Math.max(ky - hh, Math.min(oy, ky + hh));
        double dx = ox - cx, dy = oy - cy;
        return dx * dx + dy * dy < radius * radius;
    }

    void update() {
        double dt = TICK / 1000.0;
        time += dt;
        double spd = currentSpeed();
        dist += spd * 60 * dt;
        score += spd * dt;

        if (keys.contains(KeyEvent.VK_LEFT)) kx -= 3.2 * 60 * dt;
        if (keys.contains(KeyEvent.VK_RIGHT)) kx += 3.2 * 60 * dt;
        kx = Math.max(35, Math.min(W - 35, kx));

        paddlePhase += dt * (2 + spd);

        spawnTimer -= dt * 60;
        if (spawnTimer <= 0) {
            spawn();
            spawnTimer = Math.max(24, 55 - hardTimer) + Math.random() * 42;
        }
        hardTimer += dt;

        for (Obstacle o : obstacles) {
            o.y += spd * 0.9;
            o.x += Math.sin(time * 0.5 + o.sway * 7) * 0.4;
            o.spin += 0.01;
        }
        for (Splash s : splashes) s.y += spd * 0.9;
        obstacles.removeIf(o -> o.y > H + 100);
        splashes.removeIf(s -> s.y > H + 100);

        for (Obstacle o : obstacles) {
            if (o.y > ky - 120 && collide(o.x, o.y, o.size * 0.72)) {
                hurt(o.rock ? 18 : 8);
                o.y = H + 200;
                score = Math.max(0, score - 15);
                break;
            }
        }
        for (Splash s : splashes) {
            if (!s.taken && s.y > ky - 80 && collide(s.x, s.y, 12)) {
                s.taken = true;
                score += 60;
                for (int i = 0; i < 10; i++) {
                    double a = Math.random() * Math.PI * 2;
                    particles.add(new Particle(s.x, s.y, Math.cos(a) * 50, Math.sin(a) * 50, new Color(120, 220, 255), 0.5));
                }
            }
        }
        particles.removeIf(p -> !p.update(dt));
    }

    @Override public void actionPerformed(ActionEvent e) {
        if (state == State.PLAYING) update();
        repaint();
    }

    @Override public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawWater(g2);
        if (state == State.PLAYING || state == State.GAME_OVER) {
            drawSplashes(g2);
            drawObstacles(g2);
            drawKayak(g2);
            drawParticles(g2);
            drawHud(g2);
        }
        if (state == State.MENU) drawMenu(g2);
        if (state == State.GAME_OVER) drawGameOver(g2);
    }

    void drawWater(Graphics2D g2) {
        GradientPaint grad = new GradientPaint(0, 0, new Color(0x145a85), 0, H, new Color(0x0f3d5e));
        g2.setPaint(grad);
        g2.fillRect(0, 0, W, H);

        g2.setColor(new Color(255, 255, 255, 36));
        g2.setStroke(new BasicStroke(1));
        for (int i = 0; i < 9; i++) {
            double x = (i * 77 + time * 20) % (W + 60) - 30;
            for (int yy = 0; yy < H; yy += 90) {
                double off = (time * 40 + i * 50) % 90;
                g2.drawLine((int) x, (int) (yy + off), (int) x + 20, (int) (yy + off));
            }
        }

        g2.setColor(new Color(0x2e7d32));
        g2.fillRect(0, 0, 10, H);
        g2.fillRect(W - 10, 0, 10, H);
        g2.setColor(new Color(0x1b5e20));
        g2.fillRect(0, 0, 6, H);
        g2.fillRect(W - 6, 0, 6, H);
    }

    void drawKayak(Graphics2D g2) {
        double tilt = (keys.contains(KeyEvent.VK_LEFT) ? -1 : keys.contains(KeyEvent.VK_RIGHT) ? 1 : 0) * 0.12;

        g2.translate(kx, ky);
        g2.rotate(tilt);

        // hull
        Path2D hull = new Path2D.Double();
        hull.moveTo(0, -45);
        hull.quadTo(27, -25, 23, 0);
        hull.quadTo(27, 25, 0, 45);
        hull.quadTo(-27, 25, -23, 0);
        hull.quadTo(-27, -25, 0, -45);
        g2.setColor(new Color(0x2e7d32));
        g2.fill(hull);
        g2.setColor(new Color(0x1b5e20));
        g2.setStroke(new BasicStroke(3));
        g2.draw(hull);

        // cockpit
        g2.setColor(new Color(0x10271b));
        g2.fillOval((int) -16, -17, 32, 34);

        // paddler
        g2.setColor(new Color(0xf5b971));
        g2.fillOval(-11, -10, 22, 22);
        g2.setColor(new Color(0xc62828));
        g2.fillArc(-13, -13, 26, 22, 180, 180);
        g2.setColor(new Color(0x1565c0));
        g2.fillRect(-8, 8, 16, 12);
        g2.setColor(new Color(0x0d47a1));
        g2.drawRect(-8, 8, 16, 12);

        // paddle
        double phase = Math.sin(paddlePhase * 2.2);
        double ang = -0.9 + phase * 0.55;
        int pl = 62;
        int px = (int) (Math.cos(ang) * pl);
        int py = (int) (Math.sin(ang) * pl) + 8;
        g2.setColor(new Color(0x8d6e63));
        g2.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(-px, -py, px, py);
        g2.rotate(ang);
        g2.setColor(new Color(0x5d4037));
        g2.fillOval(px - 2, -5, 18, 10);
        g2.fillOval(-px - 16, -5, 18, 10);
        g2.rotate(-ang);

        g2.rotate(-tilt);
        g2.translate(-kx, -ky);

        // wake
        if (currentSpeed() > 3) {
            g2.setColor(new Color(255, 255, 255, 120));
            for (int i = 0; i < 5; i++) {
                double a = Math.random() * Math.PI * 2;
                g2.fillOval((int) (kx + Math.cos(a) * 30), (int) (ky + 48), 6, 6);
            }
        }
    }

    void drawObstacles(Graphics2D g2) {
        for (Obstacle o : obstacles) {
            if (o.rock) {
                int r = (int) o.size;
                float[] fracs = {0f, 0.7f, 1f};
                Color[] cols = {new Color(0x90a4ae), new Color(0x546e7a), new Color(0x37474f)};
                g2.setPaint(new RadialGradientPaint(
                        new Point2D.Double(o.x - r * 0.3, o.y - r * 0.3), r,
                        new Point2D.Double(o.x, o.y), fracs, cols));
                g2.fillOval((int) (o.x - r), (int) (o.y - r), r * 2, r * 2);
                g2.setColor(new Color(0x263238));
                g2.setStroke(new BasicStroke(2));
                g2.drawOval((int) (o.x - r), (int) (o.y - r), r * 2, r * 2);
                g2.setColor(new Color(255, 255, 255, 90));
                g2.setStroke(new BasicStroke(2));
                g2.drawArc((int) (o.x - r * 0.65), (int) (o.y - r * 0.7), (int) (r * 0.7), (int) (r * 0.7),
                        (int) Math.toDegrees(Math.PI * 1.2), (int) Math.toDegrees(Math.PI * 0.6));
            } else {
                g2.translate(o.x, o.y);
                g2.rotate(o.spin);
                int len = (int) (o.size * 1.8), w = (int) (o.size * 0.45);
                GradientPaint grad = new GradientPaint(0, -w, new Color(0x8d6e63), 0, w, new Color(0x3e2723));
                g2.setPaint(grad);
                g2.fillRoundRect(-len / 2, -w / 2, len, w, w, w);
                g2.setColor(new Color(0x2f1c14));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(-len / 2, -w / 2, len, w, w, w);
                g2.setColor(new Color(120, 90, 60, 150));
                g2.setStroke(new BasicStroke(1));
                g2.drawLine(-len / 2 + 8, -2, len / 2 - 8, 0);
                g2.drawLine(-len / 2 + 8, 2, len / 2 - 8, 0);
                g2.setColor(new Color(0x8d6e63));
                g2.fillOval(-len / 2 - w / 2 + 2, -w / 2, w, w);
                g2.setColor(new Color(0x4e342e));
                g2.drawOval(-len / 2 - w / 2 + 2, -w / 2, w, w);
                g2.drawOval(-len / 2 - w / 2 + 2 + w / 4, -w / 2 + w / 4, w / 2, w / 2);
                g2.rotate(-o.spin);
                g2.translate(-o.x, -o.y);
            }
        }
    }

    void drawSplashes(Graphics2D g2) {
        for (Splash s : splashes) {
            if (s.taken) continue;
            double pulse = 1 + Math.sin(time * 4 + s.wob) * 0.15;
            g2.setColor(new Color(120, 220, 255, 230));
            g2.setStroke(new BasicStroke(2));
            g2.translate(s.x, s.y);
            for (int i = 0; i < 6; i++) {
                double a = i / 6.0 * Math.PI * 2;
                g2.drawLine((int) (Math.cos(a) * 4), (int) (Math.sin(a) * 4),
                        (int) (Math.cos(a) * 10 * pulse), (int) (Math.sin(a) * 10 * pulse));
            }
            g2.translate(-s.x, -s.y);
        }
    }

    void drawParticles(Graphics2D g2) {
        for (Particle p : particles) {
            int a = (int) (255 * (p.life / p.max));
            g2.setColor(new Color(p.c.getRed(), p.c.getGreen(), p.c.getBlue(), a));
            g2.fillOval((int) p.x - 3, (int) p.y - 3, 6, 6);
        }
    }

    void drawHud(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        g2.drawString("Score: " + (int) score, 16, 30);
        g2.drawString("Speed: " + String.format("%.1f", currentSpeed()) + "x", 16, 54);
        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillRect(420, 12, 130, 16);
        g2.setColor(hull > 50 ? new Color(0x43a047) : hull > 25 ? new Color(0xf9a825) : new Color(0xe53935));
        g2.fillRect(420, 12, (int) (130 * hull / 100.0), 16);
        g2.setColor(Color.WHITE);
        g2.drawRect(420, 12, 130, 16);
        g2.drawString("Hull", 380, 26);
    }

    void drawMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(0, 0, W, H);
        g2.setFont(new Font("SansSerif", Font.BOLD, 44));
        g2.setColor(new Color(0xa5d6a7));
        g2.drawString("KAYAK RAPIDS", W / 2 - 170, 260);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g2.setColor(Color.WHITE);
        g2.drawString("Dodge rocks and logs, grab splashes for points.", W / 2 - 200, 310);
        g2.drawString("Arrow keys: steer   Up: paddle fast   Down: brake", W / 2 - 200, 338);
        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
        g2.setColor(new Color(0x43a047));
        g2.drawString("PRESS ENTER TO START", W / 2 - 140, 420);
    }

    void drawGameOver(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRect(0, 0, W, H);
        g2.setFont(new Font("SansSerif", Font.BOLD, 46));
        g2.setColor(new Color(0xe57373));
        g2.drawString("SWAMPED!", W / 2 - 110, 290);
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        g2.setColor(Color.WHITE);
        g2.drawString("Final score: " + (int) score, W / 2 - 100, 330);
        g2.setFont(new Font("SansSerif", Font.BOLD, 20));
        g2.setColor(new Color(0x43a047));
        g2.drawString("PRESS ENTER TO PLAY AGAIN", W / 2 - 150, 400);
    }

    @Override public void keyPressed(KeyEvent e) {
        keys.add(e.getKeyCode());
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (state == State.MENU || state == State.GAME_OVER) startGame();
        }
    }
    @Override public void keyReleased(KeyEvent e) { keys.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kayak Rapids");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            KayakGame game = new KayakGame();
            frame.add(game);
            frame.pack();
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
