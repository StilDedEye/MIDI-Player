package com.stilded.midiplayer;

import com.stilded.midiplayer.MidiParser;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.sound.midi.*;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.List;

public class TestMIDI extends Application {
    private static final int WHITE_KEYS = 88; // Aggiungi più tasti bianchi (da A0 a C7)
    private static final int KEY_WIDTH = 15; // Modifica la larghezza di ogni tasto per adattarlo meglio
    private static final int KEY_HEIGHT = 200; // Altezza di ogni tasto
    private static final int START_NOTE = 21; // MIDI note number per A0
    private static final double TIME_SCALE = 0.1; // Fattore di scala per il tempo (per adattarlo alla visibilità)

    private final Map<Integer, Rectangle> keyMap = new HashMap<>();
    private ScrollPane scrollPane;
    private double scrollSpeed = 0.1; // Fattore di velocità dello scroll

    private int bpm; // Variabile per il BPM del brano

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        BorderPane borderPane = new BorderPane();
        Pane root = new Pane();

        // Creazione tasti bianchi e neri
        createKeys(root);

        borderPane.setTop(root);

        // Inizializza la parte dello stackPane per le note
        Pane stackPane = new Pane();

        // Parsing del MIDI (presumibilmente dal tuo MidiParser)
        MidiParser.main(new String[]{}); // Questo chiama il parsing MIDI

        // Ordina le note in base al tempo di inizio
        List<Note> notes = extractNotesFromParser();

        // Ordina le note per tempo di inizio (in ordine crescente)
        Collections.sort(notes, Comparator.comparingLong(Note::getStartTime));

        // Aggiungi le note ordinate allo stackPane
        for (Note note : notes) {
            Rectangle previewNote = keyMap.get(note.getKey());
            if (previewNote != null) {
                // Scala il tempo di inizio e la durata in base alla finestra
                double scaledStartTime = note.getStartTime() * TIME_SCALE;  // Scala il tempo per visibilità
                double scaledDuration = note.getDuration() * TIME_SCALE;

                // Crea un nuovo rettangolo per rappresentare la nota
                Rectangle noteRectangle = new Rectangle(previewNote.getX(), scaledStartTime,
                        previewNote.getWidth(), scaledDuration);

                // Modifica la larghezza per i tasti neri
                if (previewNote.getWidth() < KEY_WIDTH) {
                    noteRectangle.setWidth(KEY_WIDTH / 2); // Tasti neri sono più stretti
                }

                noteRectangle.setFill(Color.GREEN); // Puoi cambiare colore in base alla tua preferenza

                // Aggiungi il rettangolo alla StackPane
                stackPane.getChildren().add(noteRectangle);
            }
        }

        // Imposta il ScrollPane per permettere lo scorrimento
        scrollPane = new ScrollPane(stackPane);
        scrollPane.setVvalue(0.5);
        scrollPane.setPrefHeight(500);  // Altezza dello ScrollPane
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS); // Barra di scorrimento verticale
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // Barra di scorrimento orizzontale
        borderPane.setBottom(scrollPane);  // Aggiungi lo ScrollPane al BorderPane

        // Imposta la scena
        Scene scene = new Scene(borderPane, WHITE_KEYS * KEY_WIDTH, 500);
        primaryStage.setTitle("MIDI Keyboard");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Inizia la riproduzione MIDI e illumina i tasti
        startMidiPlayback(stackPane);
    }

    private void createKeys(Pane root) {
        // Crea i tasti bianchi e neri
        for (int i = 0; i < WHITE_KEYS; i++) {
            Rectangle whiteKey = new Rectangle(i * KEY_WIDTH, 0, KEY_WIDTH, KEY_HEIGHT);
            whiteKey.setFill(Color.WHITE);
            whiteKey.setStroke(Color.BLACK);
            root.getChildren().add(whiteKey);
            keyMap.put(START_NOTE + i, whiteKey); // Mappa delle note bianche (es. A0, C1, D1...)
        }

        // Crea i tasti neri (in base alla posizione nella scala cromatica)
        for (int i = 0; i < WHITE_KEYS; i++) {
            if ((i % 7 != 2) && (i % 7 != 6)) { // Salta B e E
                Rectangle blackKey = new Rectangle(i * KEY_WIDTH + (KEY_WIDTH * 3 / 4), 0, KEY_WIDTH / 2, KEY_HEIGHT / 2);
                blackKey.setFill(Color.BLACK);
                root.getChildren().add(blackKey);
                keyMap.put(START_NOTE + i + 1, blackKey); // Mappa delle note nere
            }
        }
    }

    private List<Note> extractNotesFromParser() {
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < MidiParser.noteDurationsList.size(); i++) {
            Map<Integer, Long> noteDuration = MidiParser.noteDurationsList.get(i);
            Map<Integer, Long> noteStartTime = MidiParser.noteStartTimesList.get(i);

            Set<Integer> keys = noteDuration.keySet();
            for (int key : keys) {
                long duration = noteDuration.get(key);    // Durata della nota
                long startTime = noteStartTime.get(key);  // Tempo di inizio della nota

                // Aggiungi la nota alla lista con il relativo tempo
                notes.add(new Note(key, startTime, duration));
            }
        }
        return notes;
    }

    private void startMidiPlayback(Pane stackPane) throws Exception {
        Sequencer sequencer = MidiSystem.getSequencer();
        sequencer.open();

        // Carica il file MIDI
        InputStream is = new BufferedInputStream(new FileInputStream(new File(
                MidiParser.path
        )));
        sequencer.setSequence(is);
        bpm = (int) sequencer.getTempoInBPM(); // Ottieni il BPM dal sequencer

        // Calcola la durata di una battuta (in millisecondi) in base al BPM
        double beatDurationInMillis = 60000.0 / bpm;  // 60,000 ms / BPM per ottenere la durata di una battuta

// Aggiusta la scrollSpeed per visualizzare le note in base alla durata della battuta
        scrollSpeed = (scrollPane.getPrefHeight() / beatDurationInMillis * TIME_SCALE+0.028 );

        // Aggiungi un Receiver per monitorare NOTE_ON e NOTE_OFF
        Transmitter transmitter = sequencer.getTransmitter();
        transmitter.setReceiver(new Receiver() {
            @Override
            public void send(MidiMessage message, long timeStamp) {
                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    int key = sm.getData1(); // Nota MIDI
                    if (sm.getCommand() == ShortMessage.NOTE_ON) {
                        int velocity = sm.getData2();
                        if (velocity > 0) {
                            highlightKey(key, true);
                        } else {
                            highlightKey(key, false);
                        }
                    } else if (sm.getCommand() == ShortMessage.NOTE_OFF) {
                        highlightKey(key, false);
                    }
                }
            }

            @Override
            public void close() {
            }
        });

        // Avvia la riproduzione
        sequencer.start();

        // Crea una timeline per aggiornare la posizione dello scrollPane in base al tempo
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(10), event -> {
            long currentTime = (sequencer.getMicrosecondPosition() / 1000);  // Tempo corrente in millisecondi
            double scrollPosition = (currentTime * scrollSpeed);  // Scala la posizione in base al tempo
            scrollPane.setVvalue(scrollPosition / stackPane.getHeight());  // Aggiorna la posizione di scroll (scorre dall'alto verso il basso)
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void highlightKey(int midiNote, boolean on) {
        Rectangle key = keyMap.get(midiNote);
        if (key != null) {
            if (on) {
                key.setFill(key.getWidth() > KEY_WIDTH / 2 ? Color.ORANGE : Color.RED); // Chiaro per bianchi, scuro per neri
            } else {
                key.setFill(key.getWidth() > KEY_WIDTH / 2 ? Color.WHITE : Color.BLACK);
            }
        }
    }

    private static class Note {
        private final int key;
        private final long startTime;
        private final long duration;

        public Note(int key, long startTime, long duration) {
            this.key = key;
            this.startTime = startTime;
            this.duration = duration;
        }

        public int getKey() {
            return key;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getDuration() {
            return duration;
        }
    }
}
