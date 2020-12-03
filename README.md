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

### Proposal 1: The tool remains a command line processor 
#### Optimize on json parser

Need to test various JSON parsers w/ different Java built-in readers/writers with production-like mixtape/change to find
the best combination.
 
#### Optimize on input format

Instead of JSON, input file can be a simple text with format well defined, to avoid the need to parse the JSON and build
the objects and then apply the change.
   
### Proposal 2: Run the tool as service

When the tool as CLI, it consumes two inputs, one the state (a combination of users, songs, and playlist) and the other
, the change (playlist only) and it generates a new state (the same users, songs, but altered playlist).

The tool is therefore stateless and each time a change is needed, it has to rebuild the state and apply the change.

If the processor runs as a service, then the state (or multiple states identified by a tag, if needed) can be kept in memory
and whenever a change needs to make, all it needs is the change file itself, no more state rebuild. Thus no more the state
file (in this case, the mixtape) is needed to be re-processed.

In most use scenarios, the change file is smaller than the state file and the process time shall be much lesser.

