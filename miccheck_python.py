# miccheck_python.py — проверка микрофона с визуализацией сигнала на Python

import pyaudio
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
import threading
import time
import sys
from collections import deque

class MicCheck:
    def __init__(self):
        self.FORMAT = pyaudio.paInt16
        self.CHANNELS = 1
        self.RATE = 44100
        self.CHUNK = 1024
        self.audio = pyaudio.PyAudio()
        self.stream = None
        self.running = False
        self.data_buffer = deque(maxlen=256)  # для осциллографа

        # Настройка графика
        self.fig, (self.ax1, self.ax2) = plt.subplots(2, 1, figsize=(10, 6))
        self.fig.suptitle('🎤 MicCheck Pro — Python', fontsize=14)
        
        # Осциллограмма
        self.ax1.set_title('Осциллограмма')
        self.ax1.set_xlabel('Время (семплы)')
        self.ax1.set_ylabel('Амплитуда')
        self.ax1.set_ylim(-32768, 32767)
        self.ax1.grid(True)
        self.line, = self.ax1.plot([], [], 'b-', linewidth=0.8)

        # Спектр
        self.ax2.set_title('Спектр частот')
        self.ax2.set_xlabel('Частота (Гц)')
        self.ax2.set_ylabel('Амплитуда')
        self.ax2.set_xlim(0, 5000)
        self.ax2.grid(True)
        self.spectrum_line, = self.ax2.plot([], [], 'r-', linewidth=0.8)

        self.audio_data = np.zeros(self.CHUNK)
        self.fft_data = np.zeros(self.CHUNK // 2)

        # Текстовые элементы для статистики
        self.text = self.ax1.text(0.02, 0.95, '', transform=self.ax1.transAxes)

        print("🎤 MicCheck Pro — Python Edition")
        print("Нажмите Ctrl+C для выхода")
        print("Отображение осциллограммы и спектра...")

    def audio_callback(self, in_data, frame_count, time_info, status):
        if self.running:
            data = np.frombuffer(in_data, dtype=np.int16)
            self.audio_data = data
            self.data_buffer.extend(data)
            # FFT
            if len(data) > 0:
                fft = np.fft.fft(data)
                fft = np.abs(fft[:len(fft)//2])
                self.fft_data = fft
        return (in_data, pyaudio.paContinue)

    def update_plot(self, frame):
        if len(self.data_buffer) > 0:
            data = list(self.data_buffer)
            if len(data) > 1:
                self.line.set_data(range(len(data)), data)
                self.ax1.relim()
                self.ax1.autoscale_view()
                self.ax1.set_ylim(-32768, 32767)
            # Спектр
            if len(self.fft_data) > 0:
                freq = np.fft.fftfreq(len(self.fft_data)*2, 1/self.RATE)[:len(self.fft_data)]
                self.spectrum_line.set_data(freq, self.fft_data)
                self.ax2.relim()
                self.ax2.autoscale_view()
                self.ax2.set_xlim(0, 5000)

            # Статистика
            if len(data) > 0:
                rms = np.sqrt(np.mean(np.square(data))) if len(data) > 0 else 0
                peak = np.max(np.abs(data)) if len(data) > 0 else 0
                status = "🔴 Тишина" if peak < 100 else "🟢 Аудио обнаружено"
                self.text.set_text(f'RMS: {rms:.0f} | Пик: {peak:.0f} | {status}')

        return self.line, self.spectrum_line, self.text

    def start(self):
        self.stream = self.audio.open(format=self.FORMAT,
                                      channels=self.CHANNELS,
                                      rate=self.RATE,
                                      input=True,
                                      frames_per_buffer=self.CHUNK,
                                      stream_callback=self.audio_callback)
        self.running = True
        self.stream.start_stream()

        ani = FuncAnimation(self.fig, self.update_plot, interval=50, blit=False)
        plt.show()

        self.running = False
        self.stream.stop_stream()
        self.stream.close()
        self.audio.terminate()

if __name__ == "__main__":
    try:
        app = MicCheck()
        app.start()
    except KeyboardInterrupt:
        print("\nВыход...")
        sys.exit(0)
