// miccheck_go.go — проверка микрофона с визуализацией сигнала на Go (PortAudio + консоль)

package main

import (
	"fmt"
	"math"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gordonklaus/portaudio"
)

func main() {
	err := portaudio.Initialize()
	if err != nil {
		fmt.Println("Ошибка инициализации:", err)
		return
	}
	defer portaudio.Terminate()

	buffer := make([]float32, 1024)
	stream, err := portaudio.OpenDefaultStream(1, 0, 44100, len(buffer), func(in []float32) {
		copy(buffer, in)
	})
	if err != nil {
		fmt.Println("Ошибка открытия потока:", err)
		return
	}
	defer stream.Close()
	err = stream.Start()
	if err != nil {
		fmt.Println("Ошибка запуска:", err)
		return
	}
	defer stream.Stop()

	fmt.Println("🎤 MicCheck Pro — Go Edition")
	fmt.Println("Нажмите Ctrl+C для выхода")
	fmt.Println()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	var samples []float32
	var peak float32 = 0.0
	ticker := time.NewTicker(50 * time.Millisecond)

	for {
		select {
		case <-sigChan:
			fmt.Println("\nВыход...")
			return
		case <-ticker.C:
			// Обработка данных
			samples = append(samples, buffer...)
			if len(samples) > 500 {
				samples = samples[len(samples)-500:]
			}
			// Вычисление RMS
			var rms float32
			if len(samples) > 0 {
				var sum float32
				for _, s := range samples {
					sum += s * s
				}
				rms = float32(math.Sqrt(float64(sum / float32(len(samples)))))
			}
			// Пик
			for _, s := range samples {
				if float32(math.Abs(float64(s))) > peak {
					peak = float32(math.Abs(float64(s)))
				}
			}
			peak *= 0.99

			percent := int(math.Min(100.0, float64(rms)*200.0))
			peakPercent := int(peak * 100)
			status := "🔴 Тишина"
			if percent > 5 {
				status = "🟢 Аудио обнаружено"
			}
			fmt.Printf("\rСтатус: %-20s Громкость: %3d%% Пик: %3d%% ", status, percent, peakPercent)
			fmt.Printf("[")
			barLen := 30
			filled := percent * barLen / 100
			for i := 0; i < barLen; i++ {
				if i < filled {
					if percent > 70 {
						fmt.Print("█")
					} else if percent > 40 {
						fmt.Print("▓")
					} else {
						fmt.Print("░")
					}
				} else {
					fmt.Print(" ")
				}
			}
			fmt.Printf("]")
		}
	}
}
