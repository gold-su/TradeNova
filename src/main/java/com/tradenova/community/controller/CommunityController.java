package com.tradenova.community.controller;

import com.tradenova.community.dto.CommunityDtos.*;
import com.tradenova.community.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService service;
    @GetMapping("/posts") public Page<PostSummary> posts(Authentication a,@PageableDefault(size=20,sort="createdAt",direction=Sort.Direction.DESC) Pageable p){return service.list(userId(a),p);}
    @GetMapping("/posts/{id}") public PostDetail post(Authentication a,@PathVariable Long id){return service.detail(userId(a),id);}
    @PostMapping("/posts") @ResponseStatus(HttpStatus.CREATED) public PostDetail create(Authentication a,@Valid @RequestBody PostRequest r){return service.create(userId(a),r);}
    @PatchMapping("/posts/{id}") public PostDetail update(Authentication a,@PathVariable Long id,@Valid @RequestBody PostRequest r){return service.update(userId(a),id,r);}
    @DeleteMapping("/posts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Authentication a,@PathVariable Long id){service.deletePost(userId(a),id);}
    @GetMapping("/posts/{id}/comments") public Page<Comment> comments(Authentication a,@PathVariable Long id,@PageableDefault(size=50) Pageable p){return service.listComments(userId(a),id,p);}
    @PostMapping("/posts/{id}/comments") @ResponseStatus(HttpStatus.CREATED) public Comment comment(Authentication a,@PathVariable Long id,@Valid @RequestBody CommentRequest r){return service.addComment(userId(a),id,r);}
    @DeleteMapping("/comments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteComment(Authentication a,@PathVariable Long id){service.deleteComment(userId(a),id);}
    @PutMapping("/posts/{id}/like") public Like like(Authentication a,@PathVariable Long id){return service.like(userId(a),id);}
    @DeleteMapping("/posts/{id}/like") public Like unlike(Authentication a,@PathVariable Long id){return service.unlike(userId(a),id);}
    @PostMapping("/reports") @ResponseStatus(HttpStatus.CREATED) public Report report(Authentication a,@Valid @RequestBody ReportRequest r){return service.report(userId(a),r);}
    private Long userId(Authentication a){Object p=a.getPrincipal();return p instanceof Long id?id:Long.valueOf(p.toString());}
}
