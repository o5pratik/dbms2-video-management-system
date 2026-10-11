package edu.lab.vms;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class VideoController {
    private final VideoRepository videos;
    public VideoController(VideoRepository videos) { this.videos = videos; }
    @GetMapping("/videos") public List<Video> all() { return videos.all(); }
    @GetMapping("/videos/search") public List<Video> search(@RequestParam(defaultValue="title") String field, @RequestParam(defaultValue="") String q) { return videos.search(field, q); }
    @GetMapping("/videos/{id}") public Video one(@PathVariable long id) { return videos.one(id); }
    @PostMapping("/videos") public ResponseEntity<Video> add(@Valid @RequestBody VideoInput input) { return ResponseEntity.status(HttpStatus.CREATED).body(videos.insert(input)); }
    @PutMapping("/videos/{id}") public Video update(@PathVariable long id, @Valid @RequestBody VideoInput input) { return videos.update(id, input); }
    @DeleteMapping("/videos/{id}") public Map<String,String> delete(@PathVariable long id) { videos.delete(id); return Map.of("message", "Video deleted."); }
    @GetMapping("/categories") public List<Map<String,Object>> categories() { return videos.categories(); }
}

