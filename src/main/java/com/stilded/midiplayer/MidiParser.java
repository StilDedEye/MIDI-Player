package com.stilded.midiplayer;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;

public class MidiParser {
    static String path = "C:\\Users\\andre\\IdeaProjects\\MIDI-Player\\src\\main\\resources\\com\\stilded\\midiplayer\\MIDI\\Echoes of the Eye - Outer Wilds (wip).mid";
    static float bpm;
    static double tempoPerBattitoMs;
    static int resolution;

    // Lista di mappe per memorizzare l'inizio delle note (può esserci più di una mappa per la stessa nota)
    static List<Map<Integer, Long>> noteStartTimesList = new ArrayList<>();
    // Lista di mappe per memorizzare la durata delle note
    static List<Map<Integer, Long>> noteDurationsList = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        parseMidiFile();
    }

    public static double tickToMs(long ticks) {
        return (ticks / (double) resolution) * tempoPerBattitoMs;
    }

    public static void parseMidiFile() throws Exception {
        File midiFile = new File(path);
        Sequence sequence = MidiSystem.getSequence(midiFile);

        // Ottieni la divisione del tempo (ticks per battito)
        resolution = sequence.getResolution();
        System.out.println("Ticks per battito: " + resolution);

        // Estrai BPM dal file MIDI
        bpm = getBPM(sequence);
        System.out.println("BPM: " + bpm);

        // Calcola la durata di un battito in millisecondi
        tempoPerBattitoMs = 60000.0 / bpm;
        System.out.println("Durata di un battito: " + tempoPerBattitoMs + " ms");

        // Estrai durate delle note (tempo tra NOTE_ON e NOTE_OFF)
        extractNoteDurations(sequence);
    }

    private static float getBPM(Sequence sequence) throws InvalidMidiDataException {
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                if (message instanceof MetaMessage) {
                    MetaMessage metaMessage = (MetaMessage) message;
                    if (metaMessage.getType() == 0x51) { // Evento di cambiamento tempo
                        byte[] data = metaMessage.getData();
                        int tempo = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                        return 60000000.0f / tempo; // Calcolo BPM
                    }
                }
            }
        }
        return 120.0f; // Default BPM
    }

    private static void extractNoteDurations(Sequence sequence) {
        for (Track track : sequence.getTracks()) {
            // Mappa per memorizzare i tempi di inizio delle note
            Map<Integer, Long> noteStartMap = new HashMap<>();
            // Mappa per memorizzare i tempi di fine delle note (durata)
            Map<Integer, Long> noteEndMap = new HashMap<>();

            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                long tick = event.getTick();

                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    int key = sm.getData1();  // La chiave della nota (ad esempio, 60 per "Do centrale")
                    int velocity = sm.getData2();  // Velocità (non usata nel calcolo della durata)

                    // Se è un NOTE_ON con velocità maggiore di zero, registra l'inizio della nota
                    if (sm.getCommand() == ShortMessage.NOTE_ON && velocity > 0) {
                        long startTimeMs = Math.round(tickToMs(tick));  // Tempo di inizio in ms
                        noteStartMap.put(key, startTimeMs);  // Salva l'inizio della nota
                    }
                    // Se è un NOTE_OFF, calcola la durata e salvala
                    else if (sm.getCommand() == ShortMessage.NOTE_OFF) {
                        if (noteStartMap.containsKey(key)) {
                            long startTime = noteStartMap.get(key);  // Ottieni il tempo di inizio della nota
                            long endTime = Math.round(tickToMs(tick));  // Tempo di fine in ms
                            long durationInMs = endTime - startTime;  // Durata della nota in ms

                            // Aggiungi alla mappa della fine delle note
                            noteEndMap.put(key, durationInMs);

                            // Aggiungi alla mappa delle durate
                            System.out.println("Nota: " + key + ", Inizia a: " + startTime + " ms, Durata: " + durationInMs + " ms");

                            Map<Integer, Long> noteStart = new HashMap<>();
                            noteStart.put(key, startTime);
                            Map<Integer, Long> noteDurationMap = new HashMap<>();
                            noteDurationMap.put(key, durationInMs);
                            noteDurationsList.add(noteDurationMap);
                            noteStartTimesList.add(noteStart);
                            // Rimuovi la nota dalla mappa degli inizi per evitare duplicati
                            noteStartMap.remove(key);
                        }
                    }
                }
            }
        }
    }

}
