# changeSongList


## Build Note
Make sure gson-2.5 is added

## Execution
```
SongListProcessor mixtape.json change.json output.json
```

## Design Notes
1. the processor does NOT validate playlist (has valid user_id, song) in mixtape.com
2. changes.json has optional "addplaylist", or "removeplaylist" or "updateplaylist"
3. [add|remove|update]playlist is extended to support multiple playlist


### addplaylist
1. the user_id must be valid
2. invalid songs will be removed
3. a new unique id will be assigned to the newly added playlist
4. no checking on if the newly added playlist is a duplicate of an existing playlist

### removeplaylist
1. If the playlist-to-be-removed does not exist, ignored

### updateplaylist (aka Add existing song to existing playlist)
1. Only valid song which is not already in the playlist will be added

## Test cases

### addplaylist
 #### { user_id: "1", song_ids: ["3"] }             valid user_id/song, new playlist created 
 #### { user_id: "4", song_ids: ["5", "9", "100"] } new playlist created w/o the invalid song 100
 #### { user_id: "5", song_ids: ["200", "300"] }    no valid song, no playlist to add
 #### { user_id: "8", song_ids: ["1", "2", "3" ] }  invalid user_id, no playlist to add
 
### removeplaylist
 #### { id: "1" }     playlist "1" is removed
 #### { id: "10" }    no playlist to remove
 
### updateplaylist (aka Add existing song to existing playlist)
 #### { id: "2", song_ids: [ "31", "32" ] }  song 31,32 are added to playlist "2"
 #### { id: "3", song_ids: [ "31", "32", "12", "13", "100" ] song 31,32 are added to playlist "3" as song 12,13 duplicated and 100 invalid

## Scaling
