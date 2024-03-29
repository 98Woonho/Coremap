package com.coremap.demo.controller;

import com.coremap.demo.config.auth.PrincipalDetails;
import com.coremap.demo.domain.dto.*;
import com.coremap.demo.domain.entity.*;
import com.coremap.demo.service.ArticleService;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@Slf4j
@RequestMapping("article")
public class ArticleController {
    @Autowired
    private ArticleService articleService;

    // 게시글 작성 view
    @GetMapping("write")
    public void getWrite(@RequestParam(value = "code", required = false, defaultValue = "") String code,
                         Model model) {
        Board board = articleService.getBoard(code);

        model.addAttribute("board", board);
    }

    // 게시글 작성
    @PostMapping("write")
    @ResponseBody
    public String postWrite(@RequestParam(value = "fileId", required = false) int[] fileIdArray,
                            @RequestParam(value = "imgId", required = false) int[] imgIdArray,
                            ArticleDto articleDto) {
        if (imgIdArray == null) {
            imgIdArray = new int[0];
        }

        if (fileIdArray == null) {
            fileIdArray = new int[0];
        }

        Long articleIndexInBoard = this.articleService.write(articleDto, fileIdArray, imgIdArray);

        JSONObject responseObject = new JSONObject();
        responseObject.put("index", articleIndexInBoard);

        return responseObject.toString();
    }

    // ckeditor getImage
    @GetMapping("image")
    public ResponseEntity<byte[]> getImage(@RequestParam(value = "id") Long id) {
        ResponseEntity<byte[]> response;
        Image image = this.articleService.getImage(id);
        if (image == null) {
            response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            // 이미지 데이터를 HTTP 응답으로 전송하는 부분
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(image.getType()));
            headers.setContentLength(image.getSize());
            response = new ResponseEntity<>(image.getData(), headers, HttpStatus.OK);
        }
        return response;
    }

    // ckeditor postImage
    @PostMapping("image")
    @ResponseBody
    public String postImage(@RequestParam(value = "upload") MultipartFile file) throws IOException {
        ImageDto imageDto = new ImageDto(file);
        Image image = this.articleService.uploadImage(imageDto);

        JSONObject responseObject = new JSONObject();
        responseObject.put("url", "/article/image?id=" + image.getId());

        return responseObject.toString();
    }

    // 게시글(/article/read) 에서 첨부파일을 클릭하면 실행
    @GetMapping("file")
    public ResponseEntity<byte[]> getFile(@RequestParam(value = "id") Long id) {
        ResponseEntity<byte[]> response;
        File file = this.articleService.getFile(id);
        if (file == null) {
            response = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            /**
             * ContentDisposition : 파일 다운로드를 할 때 사용되어 웹 서버가 전송한 파일이 브라우저에서 어떻게 처리되어야 하는지를 지정
             * attachment() : 파일을 다운로드로 받음.
             * inline() : 파일이 다운로드되지 않고 웹으로 표시 해줌.
             * filename() : 다운로드 받은 파일 이름
             */
            ContentDisposition contentDisposition = ContentDisposition
                    .attachment()
                    .filename(file.getName(), StandardCharsets.UTF_8)
                    .build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(file.getType()));
            headers.setContentLength(file.getSize());
            headers.setContentDisposition(contentDisposition);
            response = new ResponseEntity<>(file.getData(), headers, HttpStatus.OK);
        }
        return response;
    }

    // 게시글 작성(/article/write)에서 첨부파일 등록
    @PostMapping("file")
    @ResponseBody
    public String postFile(@RequestParam(value = "file") MultipartFile mulitpartFile) throws IOException {
        FileDto fileDto = new FileDto(mulitpartFile);
        File file = this.articleService.uploadFile(fileDto);
        JSONObject responseObject = new JSONObject();
        responseObject.put("id", file.getId());

        return responseObject.toString();
    }

    // 게시글 view
    @GetMapping("read")
    public void getRead(@RequestParam(value = "index") Long index,
                        @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                        @RequestParam(value = "criterion", required = false) String criterion,
                        @RequestParam(value = "keyword", required = false) String keyword,
                        @RequestParam(value = "code") String code,
                        Model model) {
        Article article = articleService.getArticle(index, code);

        if (article != null) {
            Board board = articleService.getBoard(code);
            List<File> fileList = this.articleService.getFileList(article.getId());

            model.addAttribute("fileList", fileList);
            model.addAttribute("board", board);
            model.addAttribute("page", page);
            model.addAttribute("criterion", criterion);
            model.addAttribute("keyword", keyword);
            model.addAttribute("article", article);
        }
    }

    // 게시글 삭제
    @DeleteMapping("read")
    @ResponseBody
    public String deleteRead(@RequestParam(value = "id") Long id) {
        return articleService.delete(id);
    }

    // 게시글 수정 view
    @GetMapping("modify")
    public void getModify(@AuthenticationPrincipal PrincipalDetails principalDetails,
                          @RequestParam(value = "index") Long index,
                          @RequestParam(value = "code") String code,
                          Model model) {
        Board[] boards = this.articleService.getBoards();
        Article article = this.articleService.getArticle(index, code);
        if (article.getUser().getUsername().equals(principalDetails.getUsername()) || principalDetails.getRole().equals("ROLE_ADMIN")) {
            Board board = Arrays.stream(boards)
                    .filter(x -> x.getCode().equals(code))
                    .findFirst()
                    .orElse(null);

            List<File> fileList = this.articleService.getFileList(article.getId());
            model.addAttribute("board", board);
            model.addAttribute("fileList", fileList);
        } else {
            article = null;
        }
        model.addAttribute("article", article);
    }

    // 게시글 수정
    @PostMapping("modify")
    @ResponseBody
    public String postModify(@RequestParam(value = "fileId", required = false) int[] fileIdArray,
                             @RequestParam(value = "imgId", required = false) int[] imgIdArray,
                             ArticleDto articleDto) {
        if (imgIdArray == null) {
            imgIdArray = new int[0];
        }

        if (fileIdArray == null) {
            fileIdArray = new int[0];
        }

        return articleService.modify(articleDto, fileIdArray, imgIdArray);
    }

    // 댓글 불러오기
    @GetMapping("comment")
    @ResponseBody
    public String getComment(@RequestParam(value = "articleId") Long articleId, @AuthenticationPrincipal PrincipalDetails principalDetails) {
        List<Comment> commentList = articleService.getCommentList(articleId);

        JSONArray responseArray = new JSONArray();
        for (Comment comment : commentList) {
            JSONObject commentObject = new JSONObject();

            commentObject.put("id", comment.getId());
            commentObject.put("articleId", comment.getArticle().getId());
            commentObject.put("username", comment.getUser().getUsername());
            commentObject.put("nickname", comment.getUser().getNickname());
            commentObject.put("commentId", comment.getComment() != null ? comment.getComment().getId() : null);

            // 직렬화 오류 해결용 formatter
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            if (comment.getModifiedAt() == null) {
                // 수정 안 했으면 최초 작성 일시
                commentObject.put("at", comment.getWrittenAt().format(formatter));
                commentObject.put("isModified", false);
            } else {
                // 수정 했으면 수정 일시
                commentObject.put("at", comment.getModifiedAt().format(formatter));
                commentObject.put("isModified", true);
            }

            // 댓글 자기가 쓴 건지 안 쓴 건지
            commentObject.put("isMine", principalDetails != null && (principalDetails.getUsername().equals(comment.getUser().getUsername()) || principalDetails.getRole().equals("ROLE_ADMIN")));

            if (!comment.getIsDeleted()) {
                commentObject.put("content", comment.getContent());
                int likeCount = articleService.getLikeCount(comment.getId(), true);
                int dislikeCount = articleService.getDislikeCount(comment.getId(), false);
                int likeStatus = articleService.getLikeStatus(comment.getId(), principalDetails != null ? principalDetails.getUsername() : null);

                commentObject.put("likeCount", likeCount);
                commentObject.put("dislikeCount", dislikeCount);
                commentObject.put("likeStatus", likeStatus);
            }
            responseArray.add(commentObject);
        }

        return responseArray.toString();
    }

    // 댓글 작성
    @PostMapping("comment")
    @ResponseBody
    public String postComment(CommentDto commentDto) {
        return articleService.writeComment(commentDto);
    }

    // 댓글 수정
    @PatchMapping("comment")
    @ResponseBody
    public String patchComment(CommentDto commentDto) {
        return articleService.updateComment(commentDto);
    }

    // 댓글 삭제
    @DeleteMapping("comment")
    @ResponseBody
    public String deleteComment(@RequestParam(value = "id") Long id) {
        return articleService.deleteComment(id);
    }

    // 댓글 좋아요/싫어요 수정
    @PutMapping("commentLike")
    @ResponseBody
    public String putCommentLike(@RequestParam(value = "commentId") Long commentId,
                                 @RequestParam(value = "status", required = false) Boolean status,
                                 @AuthenticationPrincipal PrincipalDetails principalDetails) {
        String result = articleService.updateCommentLike(commentId, status, principalDetails.getUsername());
        JSONObject responseObject = new JSONObject();

        responseObject.put("result", result.toLowerCase());
        if (result.equals("SUCCESS")) {
            int likeCount = articleService.getLikeCount(commentId, true);
            int dislikeCount = articleService.getDislikeCount(commentId, false);
            int likeStatus = articleService.getLikeStatus(commentId, principalDetails.getUsername());

            responseObject.put("likeCount", likeCount);
            responseObject.put("dislikeCount", dislikeCount);
            responseObject.put("likeStatus", likeStatus);
        }
        return responseObject.toString();
    }
}
