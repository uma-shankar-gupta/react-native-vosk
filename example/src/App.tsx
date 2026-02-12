import { useState, useEffect } from 'react';

import { StyleSheet, View, Text, Button } from 'react-native';
import * as vosk from 'react-native-vosk';

export default function App() {
  const [ready, setReady] = useState<Boolean>(false);
  const [recognizing, setRecognizing] = useState<Boolean>(false);
  const [streaming, setStreaming] = useState<Boolean>(false);
  const [result, setResult] = useState<string | undefined>();

  const load = () => {
    vosk
      .loadModel('model-fr-fr')
      // .loadModel('model-en-us')
      .then(() => setReady(true))
      .catch((e) => console.error(e));
  };

  const record = () => {
    vosk
      .start()
      .then(() => {
        console.log('Starting recognition...');
        setRecognizing(true);
      })
      .catch((e) => console.error(e));
  };

  const recordGrammar = () => {
    vosk
      .start({ grammar: ['cool', 'application', '[unk]'] })
      .then(() => {
        console.log('Starting recognition with grammar...');
        setRecognizing(true);
      })
      .catch((e) => console.error(e));
  };

  const recordTimeout = () => {
    vosk
      .start({ timeout: 5000 })
      .then(() => {
        console.log('Starting recognition with timeout...');
        setRecognizing(true);
      })
      .catch((e) => console.error(e));
  };

  const stop = () => {
    vosk.stop();
    console.log('Stoping recognition...');
    setRecognizing(false);
  };

  const unload = () => {
    vosk.unload();
    setReady(false);
    setRecognizing(false);
  };

  const testStreaming = async () => {
    try {
      console.log('Starting streaming session...');
      await vosk.startStreaming();
      setStreaming(true);

      // Generate synthetic audio chunks with varying frequencies
      // This simulates audio activity (though not actual speech)
      console.log('Feeding synthetic audio chunks...');
      for (let chunkIndex = 0; chunkIndex < 20; chunkIndex++) {
        const chunk: number[] = [];

        // Generate 4096 bytes of audio data
        for (let i = 0; i < 4096; i++) {
          // Create varying amplitude sine wave
          // Frequency varies between 100-1000 Hz to simulate speech-like patterns
          const time = (chunkIndex * 4096 + i) / 16000; // Sample rate 16kHz
          const freq = 200 + Math.sin(time) * 100; // Varying frequency
          const amplitude = 5000 + Math.sin(time * 0.5) * 3000; // Varying amplitude
          const sample = Math.floor(Math.sin(2 * Math.PI * freq * time) * amplitude);

          // Convert to signed 16-bit value (-32768 to 32767)
          const byte = Math.max(-128, Math.min(127, Math.floor(sample / 256)));
          chunk.push(byte);
        }

        await vosk.feedChunk(chunk);
        // Small delay to simulate real-time streaming
        await new Promise((resolve) => setTimeout(resolve, 100));
      }

      console.log('Stopping streaming...');
      const finalResult = await vosk.stopStreaming();
      console.log('Streaming result:', finalResult);
      setResult(finalResult);
      setStreaming(false);
    } catch (e) {
      console.error('Streaming error:', e);
      setStreaming(false);
    }
  };

  useEffect(() => {
    const resultEvent = vosk.onResult((res) => {
      console.log('An onResult event has been caught: ' + res);
      setResult(res);
    });

    const partialResultEvent = vosk.onPartialResult((res) => {
      setResult(res);
    });

    const finalResultEvent = vosk.onFinalResult((res) => {
      setResult(res);
    });

    const errorEvent = vosk.onError((e) => {
      console.error(e);
    });

    const timeoutEvent = vosk.onTimeout(() => {
      console.log('Recognizer timed out');
      setRecognizing(false);
    });

    return () => {
      resultEvent.remove();
      partialResultEvent.remove();
      finalResultEvent.remove();
      errorEvent.remove();
      timeoutEvent.remove();
    };
  }, []);

  return (
    <View style={styles.container}>
      <Button
        onPress={ready ? unload : load}
        title={ready ? 'Unload model' : 'Load model'}
        color="blue"
      />

      {!recognizing && (
        <View style={styles.recordingButtons}>
          <Button
            title="Record"
            onPress={record}
            disabled={!ready}
            color="green"
          />

          <Button
            title="Record with grammar"
            onPress={recordGrammar}
            disabled={!ready}
            color="green"
          />

          <Button
            title="Record with timeout"
            onPress={recordTimeout}
            disabled={!ready}
            color="green"
          />

          <Button
            title="Test Streaming API"
            onPress={testStreaming}
            disabled={!ready || streaming}
            color="purple"
          />
        </View>
      )}

      {recognizing && <Button onPress={stop} title="Stop" color="red" />}

      <Text>Recognized word:</Text>
      <Text>{result}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 25,
    flex: 1,
    display: 'flex',
    textAlign: 'center',
    alignItems: 'center',
    justifyContent: 'center',
  },
  recordingButtons: {
    gap: 15,
    display: 'flex',
  },
});
