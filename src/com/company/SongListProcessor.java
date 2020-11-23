package com.company;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SongListProcessor {

    static final String KEY_USERS = "users";
    static final String KEY_PALYLISTS = "playlists";
    static final String KEY_SONGS = "songs";

    static final String KEY_REMOVE_PLAYLISTS = "removeplaylists";
    static final String KEY_ADD_PLAYLISTS = "addplaylists";
    static final String KEY_UPDATE_PLAYLISTS = "updateplaylists";

    /**
     * Class to model user and used to be serialized to output file
     */
    static class User {

        String id;
        String name;

        public User(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /**
     * Class to model playlist and used to be serialized to output file
     */
    static class Playlist {

        String id;
        String user_id;
        ArrayList<String> song_ids;

        public Playlist(String id, String user_id, ArrayList<String> song_ids) {
            this.id = id;
            this.user_id = user_id;
            this.song_ids = song_ids;
        }
    }

    /**
     * Class to model song and used to be serialized to output file
     */
    static class Song {

        String id;
        String artist;
        String title;

        public Song(String id, String artist, String title) {
            this.id = id;
            this.artist = artist;
            this.title = title;
        }
    }

    /**
     * Class used to be serialized to output file
     */
    static class Config {

        ArrayList<User> users;
        ArrayList<Playlist> playlists;
        ArrayList<Song> songs;

        public Config(Map<String, User> userMap,
                      Map<String, Playlist> playlistMap,
                      Map<String, Song> songMap) {

            users = new ArrayList<User>();
            for (Map.Entry<String, User> me : userMap.entrySet()) {
                users.add(me.getValue());
            }

            playlists = new ArrayList<Playlist>();
            for (Map.Entry<String, Playlist> pe : playlistMap.entrySet()) {
                playlists.add(pe.getValue());
            }

            songs = new ArrayList<Song>();
            for (Map.Entry<String, Song> se : songMap.entrySet()) {
                songs.add(se.getValue());
            }
        }
    }

    private String inputFile;
    private String outputFile;
    private String changeFile;

    private Map<String, User> usersMap;
    private Map<String, Playlist> playlistMap;
    private Map<String, Song> songsMap;

    private int playlistMaxId;

    public static void main(String[] args) {

        if (args == null || args.length < 3 ) {
            usage();
        }

        checkFileExists(args[0], "Input file");
        checkFileExists(args[1], "Change file");
        // Need NOT to check the output file
        // as it will be created if not exist or
        // overwritten otherwise

        final SongListProcessor processor = new SongListProcessor(args);
        processor.run();
    }

    public SongListProcessor(String[] args) {

        inputFile = args[0];
        changeFile = args[1];
        outputFile = args[2];

        usersMap = new HashMap<String, User>();
        playlistMap = new HashMap<String, Playlist>();
        songsMap = new HashMap<String, Song>();

        playlistMaxId = 0;
    }

    public void run() {
        parseInput();
        applyChange();
        generateOutput();
    }

    /**
     *
     */
    private void parseInput() {

        try {

            final Gson gson = new Gson();
            final Reader reader = Files.newBufferedReader(Paths.get(inputFile));
            final Map<?, ?> map = gson.fromJson(reader, Map.class);

            /*
              users: [ { id: "1", name: "Albin Jaye" }, {...} ]
            */

            if (map.containsKey(KEY_USERS)) {
                final ArrayList<Map<String, String>> ul = (ArrayList<Map<String, String>>) map.get(KEY_USERS);
                //Type listType = new TypeToken<ArrayList<User>>() {}.getType();
                //userList = gson.fromJson(usersString, listType);
                for (Map<String, String> u : ul) {
                    usersMap.put(u.get("id"), new User(u.get("id"), u.get("name")));
                }
            }

            /*
                playlists: [ { id: "1", user_id: "2", song_ids: [ "8", "32" ] }, {...} ]
             */
            if (map.containsKey(KEY_PALYLISTS)) {
                final ArrayList<Map<String, ?>> pl = (ArrayList<Map<String, ?>>) map.get(KEY_PALYLISTS);
                for (Map<String, ?> p : pl) {
                    final String id = (String) p.get("id");
                    final String user_id = (String) p.get("user_id");
                    final ArrayList<String> songIds = (ArrayList<String>) p.get("song_ids");
                    playlistMap.put(id, new Playlist(id, user_id, songIds));
                    updatePlayListMaxId(id);
                }
            }

            /*
                songs: [ { id: "1", artist: "Camila Cabello", title: "Never Be the Same" }, {...} ]
             */
            if (map.containsKey(KEY_SONGS)) {
                final ArrayList<Map<String, ?>> sl = (ArrayList<Map<String, ?>>) map.get(KEY_SONGS);
                for (Map<String, ?> s : sl) {
                    final String id = (String) s.get("id");
                    final String artist = (String) s.get("artist");
                    final String title = (String) s.get("title");
                    songsMap.put(id, new Song(id, artist, title));
                }

            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     *  Change file
     *  1. Add one playlist,
     *  2. Remove one existing playlist,
     *  3. Add an existing song to an existing playlist
     *
     *  The order of processing is
     *  1. Remove existing playlist
     *       if the id given does NOT exist, do NOTHING
     *       if the id given does exist, the playlist will be removed
     *
     *  2. Add new playlist
     *       if the user_id does NOT exist, skip
     *       if the user_id does exist, a new playlist will be created
     *          any non-existing songs will be NOT included in the newly added playlist
     *          NO checking on if the newly to be added playlist is duplicate of an existing one
     *          A new unique id will be generated for the new playlist
     *
     *  3. Add existing songs to existing playlist
     *
     */
    private void applyChange() {

        try {

            final Gson gson = new Gson();
            final Reader reader = Files.newBufferedReader(Paths.get(changeFile));
            final Map<?, ?> map = gson.fromJson(reader, Map.class);

            /*
                 removeplaylists: [ { id: "1" }, { id: "2" } ]
            */
            if (map.containsKey(KEY_REMOVE_PLAYLISTS)) {
                final ArrayList<Map<String, String>> pl = (ArrayList<Map<String, String>>) map.get(KEY_REMOVE_PLAYLISTS);
                for (Map<String, String> p : pl) {
                    playlistMap.remove(p.get("id"));
                }
            }

            /*
                addplaylists: [ { user_id: "5", song_ids: [ "5", "9", "20" ] } ]
            */
            if (map.containsKey(KEY_ADD_PLAYLISTS)) {
                final ArrayList<Map<String, ?>> pl = (ArrayList<Map<String, ?>>) map.get(KEY_ADD_PLAYLISTS);
                for (Map<String, ?> p : pl) {
                    final String user_id = (String) p.get("user_id");

                    // Add the new playlist only if the user_id is valid
                    if (usersMap.containsKey(user_id)) {

                        // To remove non-exist songs
                        final ArrayList<String> songIds = (ArrayList<String>) p.get("song_ids");
                        final Iterator<String> itr = songIds.iterator();
                        while (itr.hasNext()) {
                            final String songId = itr.next();
                            if (!songsMap.containsKey(songId)) {
                                itr.remove();
                            }
                        }

                        // Only create the new playlist if has at least one valid song
                        if (songIds.size() > 0) {
                            final String playListId = getNewPlayListId();
                            playlistMap.put(playListId, new Playlist(playListId, user_id, songIds));
                        }
                    }
                }
            }

            /*
              updateplaylists: [ { id: "1", song_ids: [ "35" ] }, {...} ]
            */
            if (map.containsKey(KEY_UPDATE_PLAYLISTS)) {
                final ArrayList<Map<String, ?>> pl = (ArrayList<Map<String, ?>>) map.get(KEY_UPDATE_PLAYLISTS);
                for (Map<String, ?> p : pl) {

                    final String id = (String) p.get("id");

                    // Update only existing playlist
                    if (playlistMap.containsKey(id)) {

                        final ArrayList<String> originalSongIds = playlistMap.get(id).song_ids;
                        final ArrayList<String> additionalSongIds = (ArrayList<String>) p.get("song_ids");
                        final Iterator<String> itr = additionalSongIds.iterator();
                        while (itr.hasNext()) {
                            final String songId = itr.next();
                            //Add valid songId which is not in the original songs
                            if (songsMap.containsKey(songId) && !originalSongIds.contains(songId)) {
                                originalSongIds.add(songId);
                            }
                        }

                    }
                }

            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     *
     *    Generate the output file containing the changes to the input file
     */
    private void generateOutput() {

        try {

            final Gson gson = new GsonBuilder().setPrettyPrinting().create();
            final Config config = new Config(usersMap, playlistMap, songsMap);
            final Writer writer = Files.newBufferedWriter(Paths.get(outputFile));
            gson.toJson(config, writer);
            writer.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     *  To update max id in the playlist
     *
     * @param id
     */
    private void updatePlayListMaxId(String id) {

        try {
            final int currentId = Integer.parseInt(id);
            playlistMaxId = Math.max(currentId, playlistMaxId);
        } catch (NumberFormatException e) {
            // if the id is not an integer, do not update
        }
    }

    /**
     *  To get the maxId for newly added playlist and increment it for the next
     *
     * @return
     */
    private String getNewPlayListId() {
        playlistMaxId++;
        return String.valueOf(playlistMaxId);
    }

    /**
     *
     *    Helper to check file existence
     *
     * @param fileName
     * @param message
     */
    private static void checkFileExists(String fileName, String message) {

        final File fileToCheck = new File(fileName);
        if (!fileToCheck.exists()) {
            System.out.println( message + "{ " + fileName + " } does NOT exist");
            System.exit(1);
        }
    }

    private static void usage() {
        System.out.println("Usage: SongListProcessor <input_file>  <change_file>  <output_file>");
        System.exit(1);
    }
}
