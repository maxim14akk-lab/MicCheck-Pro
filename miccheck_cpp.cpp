// miccheck_cpp.cpp — проверка микрофона с визуализацией сигнала на C++ (PortAudio + SFML)

#include <iostream>
#include <cmath>
#include <vector>
#include <deque>
#include <SFML/Graphics.hpp>
#include <portaudio.h>

const int SAMPLE_RATE = 44100;
const int FRAMES_PER_BUFFER = 256;
const int HISTORY_SIZE = 500;

std::deque<float> samples;
float current_peak = 0.0f;
bool running = true;

static int paCallback(const void* inputBuffer, void* outputBuffer,
                      unsigned long framesPerBuffer,
                      const PaStreamCallbackTimeInfo* timeInfo,
                      PaStreamCallbackFlags statusFlags,
                      void* userData) {
    const float* in = static_cast<const float*>(inputBuffer);
    if (in) {
        for (unsigned long i = 0; i < framesPerBuffer; ++i) {
            samples.push_back(in[i]);
            if (samples.size() > HISTORY_SIZE) samples.pop_front();
            float abs_val = std::abs(in[i]);
            if (abs_val > current_peak) current_peak = abs_val;
        }
    }
    return paContinue;
}

int main() {
    Pa_Initialize();
    PaStream* stream;
    Pa_OpenDefaultStream(&stream, 1, 0, paFloat32, SAMPLE_RATE,
                         FRAMES_PER_BUFFER, paCallback, nullptr);
    Pa_StartStream(stream);

    sf::RenderWindow window(sf::VideoMode(800, 500), "🎤 MicCheck Pro — C++");
    window.setFramerateLimit(30);

    sf::Font font;
    if (!font.loadFromFile("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf")) {
        // fallback
    }

    sf::Text statusText("Статус: Ожидание...", font, 16);
    statusText.setPosition(10, 10);
    statusText.setFillColor(sf::Color::White);

    sf::Text peakText("Пик: 0%", font, 16);
    peakText.setPosition(10, 35);
    peakText.setFillColor(sf::Color::White);

    sf::RectangleShape bar(sf::Vector2f(200, 20));
    bar.setPosition(10, 60);
    bar.setFillColor(sf::Color::Green);

    while (window.isOpen()) {
        sf::Event event;
        while (window.pollEvent(event)) {
            if (event.type == sf::Event::Closed)
                window.close();
        }

        window.clear(sf::Color::Black);

        // Отображение волны
        if (samples.size() > 1) {
            sf::VertexArray lines(sf::LineStrip, samples.size());
            int x = 50;
            int base_y = 300;
            for (size_t i = 0; i < samples.size(); ++i) {
                float x_pos = 50 + i * (700.0f / samples.size());
                float y_pos = base_y - samples[i] * 200.0f;
                lines[i] = sf::Vertex(sf::Vector2f(x_pos, y_pos), sf::Color::Cyan);
            }
            window.draw(lines);
        }

        // Уровень громкости
        float rms = 0.0f;
        if (!samples.empty()) {
            for (float s : samples) rms += s*s;
            rms = std::sqrt(rms / samples.size());
        }
        float percent = std::min(100.0f, rms * 200.0f);
        bar.setSize(sf::Vector2f(percent * 2, 20));
        if (percent > 70) bar.setFillColor(sf::Color::Red);
        else if (percent > 40) bar.setFillColor(sf::Color::Yellow);
        else bar.setFillColor(sf::Color::Green);
        window.draw(bar);

        // Тексты
        statusText.setString(percent > 5 ? "✅ Аудио обнаружено" : "🔴 Тишина");
        statusText.setFillColor(percent > 5 ? sf::Color::Green : sf::Color::Red);
        window.draw(statusText);
        peakText.setString("Громкость: " + std::to_string((int)percent) + "%  Пик: " + std::to_string((int)(current_peak * 100)) + "%");
        window.draw(peakText);

        window.display();
    }

    Pa_StopStream(stream);
    Pa_CloseStream(stream);
    Pa_Terminate();
    return 0;
}
