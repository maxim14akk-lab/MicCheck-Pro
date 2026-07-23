// MicCheckJava.java — проверка микрофона с визуализацией сигнала на Java

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.sound.sampled.*;
import java.util.*;
import java.util.List;

public class MicCheckJava extends JFrame {
    private static final int SAMPLE_RATE = 44100;
    private static final int SAMPLE_SIZE = 1024;
    private boolean running = false;
    private TargetDataLine line;
    private List<Float> samples = new ArrayList<>();
    private float peak = 0.0f;

    private JPanel graphPanel;
    private JLabel statusLabel, levelLabel, peakLabel;
    private JProgressBar levelBar;

    public MicCheckJava() {
        setTitle("🎤 MicCheck Pro — Java");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        createUI();
        startAudio();
    }

    private void createUI() {
        // Верхняя панель с информацией
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        statusLabel = new JLabel("Статус: Ожидание...");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0; gbc.gridy = 0;
        infoPanel.add(statusLabel, gbc);

        levelLabel = new JLabel("Громкость: 0%");
        gbc.gridx = 0; gbc.gridy = 1;
        infoPanel.add(levelLabel, gbc);

        peakLabel = new JLabel("Пик: 0%");
        gbc.gridx = 1; gbc.gridy = 1;
        infoPanel.add(peakLabel, gbc);

        levelBar = new JProgressBar(0, 100);
        levelBar.setPreferredSize(new Dimension(200, 20));
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        infoPanel.add(levelBar, gbc);

        add(infoPanel, BorderLayout.NORTH);

        // График
        graphPanel = new GraphPanel();
        add(graphPanel, BorderLayout.CENTER);

        // Нижняя панель
        JPanel bottomPanel = new JPanel();
        JButton closeBtn = new JButton("Закрыть");
        closeBtn.addActionListener(e -> { running = false; System.exit(0); });
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        // Таймер обновления UI
        Timer timer = new Timer(30, e -> {
            updateUI();
            graphPanel.repaint();
        });
        timer.start();
    }

    private void startAudio() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            running = true;

            new Thread(() -> {
                byte[] buffer = new byte[SAMPLE_SIZE * 2];
                while (running) {
                    int bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        float[] samplesFloat = new float[bytesRead / 2];
                        for (int i = 0; i < samplesFloat.length; i++) {
                            samplesFloat[i] = (buffer[i*2] | (buffer[i*2+1] << 8)) / 32768.0f;
                        }
                        synchronized (samples) {
                            for (float s : samplesFloat) {
                                samples.add(s);
                            }
                            while (samples.size() > 500) samples.remove(0);
                            float p = 0;
                            for (float s : samples) if (Math.abs(s) > p) p = Math.abs(s);
                            peak = p;
                        }
                    }
                }
            }).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Ошибка: " + e.getMessage());
        }
    }

    private void updateUI() {
        float rms = 0;
        synchronized (samples) {
            if (!samples.isEmpty()) {
                for (float s : samples) rms += s*s;
                rms = (float)Math.sqrt(rms / samples.size());
            }
        }
        int percent = (int)(Math.min(1.0f, rms * 5) * 100);
        int peakPercent = (int)(peak * 100);
        statusLabel.setText(percent > 5 ? "✅ Аудио обнаружено" : "🔴 Тишина");
        statusLabel.setForeground(percent > 5 ? Color.GREEN : Color.RED);
        levelLabel.setText("Громкость: " + percent + "%");
        peakLabel.setText("Пик: " + peakPercent + "%");
        levelBar.setValue(percent);
        if (percent > 70) levelBar.setForeground(Color.RED);
        else if (percent > 40) levelBar.setForeground(Color.ORANGE);
        else levelBar.setForeground(Color.GREEN);
    }

    class GraphPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int baseY = h / 2;
            g2.setColor(Color.CYAN);
            synchronized (samples) {
                if (samples.size() > 1) {
                    int n = samples.size();
                    for (int i = 1; i < n; i++) {
                        int x1 = (i-1) * w / n;
                        int y1 = baseY - (int)(samples.get(i-1) * baseY * 0.9f);
                        int x2 = i * w / n;
                        int y2 = baseY - (int)(samples.get(i) * baseY * 0.9f);
                        g2.drawLine(x1, y1, x2, y2);
                    }
                }
            }
            // Центральная линия
            g2.setColor(Color.GRAY);
            g2.drawLine(0, baseY, w, baseY);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MicCheckJava().setVisible(true));
    }
}
