// MicCheckCSharp.cs — проверка микрофона с визуализацией сигнала на C# (NAudio + WinForms)

using System;
using System.Collections.Generic;
using System.Drawing;
using System.Windows.Forms;
using NAudio.Wave;

class MicCheckForm : Form
{
    private WaveInEvent waveIn;
    private List<float> samples = new List<float>();
    private float peak = 0.0f;
    private Timer timer;
    private Panel graphPanel;
    private Label statusLabel, levelLabel, peakLabel;
    private ProgressBar levelBar;

    public MicCheckForm()
    {
        Text = "🎤 MicCheck Pro — C#";
        Size = new Size(700, 500);
        StartPosition = FormStartPosition.CenterScreen;

        InitializeComponents();
        StartAudio();
    }

    private void InitializeComponents()
    {
        // Верхняя панель
        var infoPanel = new FlowLayoutPanel();
        infoPanel.Dock = DockStyle.Top;
        infoPanel.Height = 80;
        infoPanel.FlowDirection = FlowDirection.TopDown;

        statusLabel = new Label { Text = "Статус: Ожидание...", Font = new Font("Arial", 12, FontStyle.Bold), AutoSize = true };
        infoPanel.Controls.Add(statusLabel);

        var levelPanel = new FlowLayoutPanel { FlowDirection = FlowDirection.LeftToRight, AutoSize = true };
        levelLabel = new Label { Text = "Громкость: 0%", AutoSize = true };
        peakLabel = new Label { Text = "Пик: 0%", AutoSize = true };
        levelBar = new ProgressBar { Width = 200, Height = 20, Minimum = 0, Maximum = 100 };
        levelPanel.Controls.Add(levelLabel);
        levelPanel.Controls.Add(peakLabel);
        levelPanel.Controls.Add(levelBar);
        infoPanel.Controls.Add(levelPanel);

        Controls.Add(infoPanel);

        // График
        graphPanel = new Panel { Dock = DockStyle.Fill, BackColor = Color.Black };
        graphPanel.Paint += GraphPanel_Paint;
        Controls.Add(graphPanel);

        // Таймер обновления
        timer = new Timer { Interval = 30 };
        timer.Tick += (s, e) => UpdateUI();
        timer.Start();
    }

    private void StartAudio()
    {
        try
        {
            waveIn = new WaveInEvent();
            waveIn.DeviceNumber = 0;
            waveIn.WaveFormat = new WaveFormat(44100, 16, 1);
            waveIn.DataAvailable += OnDataAvailable;
            waveIn.StartRecording();
        }
        catch (Exception ex)
        {
            MessageBox.Show("Ошибка: " + ex.Message);
        }
    }

    private void OnDataAvailable(object sender, WaveInEventArgs e)
    {
        float[] buffer = new float[e.BytesRecorded / 2];
        for (int i = 0; i < buffer.Length; i++)
        {
            buffer[i] = BitConverter.ToInt16(e.Buffer, i * 2) / 32768.0f;
        }
        lock (samples)
        {
            foreach (float s in buffer)
            {
                samples.Add(s);
                if (Math.Abs(s) > peak) peak = Math.Abs(s);
            }
            while (samples.Count > 500) samples.RemoveAt(0);
        }
    }

    private void UpdateUI()
    {
        float rms = 0;
        lock (samples)
        {
            if (samples.Count > 0)
            {
                foreach (float s in samples) rms += s * s;
                rms = (float)Math.Sqrt(rms / samples.Count);
            }
        }
        int percent = (int)(Math.Min(1.0f, rms * 5) * 100);
        int peakPercent = (int)(peak * 100);
        statusLabel.Text = percent > 5 ? "✅ Аудио обнаружено" : "🔴 Тишина";
        statusLabel.ForeColor = percent > 5 ? Color.Green : Color.Red;
        levelLabel.Text = "Громкость: " + percent + "%";
        peakLabel.Text = "Пик: " + peakPercent + "%";
        levelBar.Value = percent;
        levelBar.ForeColor = percent > 70 ? Color.Red : (percent > 40 ? Color.Orange : Color.Green);
        graphPanel.Invalidate();
        peak *= 0.99f; // затухание пика
    }

    private void GraphPanel_Paint(object sender, PaintEventArgs e)
    {
        var g = e.Graphics;
        int w = graphPanel.Width;
        int h = graphPanel.Height;
        int baseY = h / 2;
        g.Clear(Color.Black);
        using (var pen = new Pen(Color.Cyan, 1))
        {
            lock (samples)
            {
                if (samples.Count > 1)
                {
                    int n = samples.Count;
                    for (int i = 1; i < n; i++)
                    {
                        int x1 = (i - 1) * w / n;
                        int y1 = baseY - (int)(samples[i - 1] * baseY * 0.9f);
                        int x2 = i * w / n;
                        int y2 = baseY - (int)(samples[i] * baseY * 0.9f);
                        g.DrawLine(pen, x1, y1, x2, y2);
                    }
                }
            }
        }
        // Центральная линия
        using (var pen = new Pen(Color.Gray, 1))
        {
            g.DrawLine(pen, 0, baseY, w, baseY);
        }
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        waveIn?.StopRecording();
        waveIn?.Dispose();
        base.OnFormClosing(e);
    }

    [STAThread]
    public static void Main()
    {
        Application.EnableVisualStyles();
        Application.Run(new MicCheckForm());
    }
}
