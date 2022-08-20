package com.sparta.instagramProject.service;

import com.amazonaws.services.s3.AmazonS3;
import com.sparta.instagramProject.dto.ArticleRequestDto;
import com.sparta.instagramProject.dto.ArticleResponseDto;
import com.sparta.instagramProject.dto.S3Dto;
import com.sparta.instagramProject.image.S3Uploader;
import com.sparta.instagramProject.model.*;
import com.sparta.instagramProject.repository.DeletedUrlPathRepository;
import com.sparta.instagramProject.repository.HeartRepository;
import com.sparta.instagramProject.repository.MemberRepository;
import com.sparta.instagramProject.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor //final로 선언한 변수가 있으면 꼭 생성해달라는 것
@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final S3Uploader s3Uploader;
    private final AmazonS3 amazonS3Client;
    private final DeletedUrlPathRepository deletedUrlPathRepository;
    private final MemberRepository memberRepository;
    private final HeartRepository heartRepository;

    @Value("${cloud.aws.s3.bucket}")
    public String bucket;  // S3 버킷 이름


    public String getNickname() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }


    @Transactional
    public Boolean registerArticle(ArticleRequestDto requestDto, List<MultipartFile> multipartFile) throws IOException {
//        if (multipartFile.isEmpty())
//            throw new IllegalArgumentException("이미지 파일을 넣어주세요.");
        if (requestDto.getContent() == null || requestDto.getNickname() == null)
            throw new IllegalArgumentException("내용을 입력하세요");

        String nickname = getNickname();
        Article article = Article.builder()
                .nickname(nickname)
                .content(requestDto.getContent())
                .isLike(false)
                .build();
        articleRepository.save(article);
//        if (!multipartFile.isEmpty()) {
//            for (MultipartFile file : multipartFile) {
//                S3Dto s3Dto = s3Uploader.upload(file);
//                Image image = Image.builder()
//                        .imgUrl(s3Dto.getUploadImageUrl())
//                        .urlPath(s3Dto.getFileName())
//                        .article(article)
//                        .build();
//                article.addImg(image);
//            }
//        }

        return true;
    }

    //S3 필요없는 곳
    @Transactional
    public List<ArticleResponseDto> getArticles(String username) {
        String nickname = username;
        List<Article> articleList = articleRepository.findAll();
        List<ArticleResponseDto> responseDtos = new ArrayList<>();
        for (Article article : articleList) {
            article.setTimeMsg(Time.calculateTime(article.getCreatedAt()));
            if (heartRepository.existsByNicknameAndArticle(nickname, article)) {
                article.setIsLike(true);
            }
            responseDtos.add(ArticleResponseDto.builder()
                    .id(article.getId())
                    .commentCnt((long) article.getCommentList().size())
                    .imgList(article.getImgList())
                    .heartCnt((long) article.getHeartList().size())
                    .content(article.getContent())
                    .timeMsg(article.getTimeMsg())
                    .isLike(article.getIsLike())
                    .nickname(article.getNickname())
                    .createdAt(article.getCreatedAt())
                    .build());
        }
//        List<Camp> campList = postRepository.findAll();
//        List<CampDto> campDtos = new ArrayList<>();
//        for (Camp camp : campList) {
////            CampDto campDto = CampMapper.INSTANCE.campToDto(camp);
//            campDtos.add(camp);
//        }
//        return campDtos;
        return responseDtos;
    }

    @Transactional
    public Article showArticleDetail(Long postId) {
        Article article = articleRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 존재하지 않습니다."));
        article.setTimeMsg(Time.calculateTime(article.getCreatedAt()));
        List<Comment> commentList = article.getCommentList();
        for (Comment comment : commentList) {
            comment.setTimeMsg(Time.calculateTime(comment.getCreatedAt()));
        }
        return article;
    }


//    @Transactional
//    public Article update(Long id, ArticleRequestDto requestDto) {
//        Article article = postRepository.findById(id).orElseThrow( //[3번]  수정할 id에 해당하는 데이터를 repo에서 찾고 해당id를 갖는 memo를 호출한다.
//                () -> new IllegalArgumentException("추천글이 존재하지 않습니다")
//        );
//        if (!getNickname().equals(article.getNickname())) {
//            throw new IllegalArgumentException("작성자만 수정할 수 있습니다.");
//        }
//        article.update(requestDto);
//        return article;
//    }

    public boolean delete(Long postId) {
        Article article = articleRepository.findById(postId).orElseThrow(
                () -> new IllegalArgumentException("메모가 존재하지 않습니다")
        );
        if (!getNickname().equals(article.getNickname())) {
            throw new IllegalArgumentException("작성자만 삭제할 수 있습니다.");
        }

        DeletedUrlPath deletedUrlPath = new DeletedUrlPath();
        List<Image> imageList = article.getImgList();
        for (Image image : imageList) {
            deletedUrlPath.setDeletedUrlPath(image.getUrlPath());
            deletedUrlPathRepository.save(deletedUrlPath);
            article.deleteImg(image);
        }

//            removeS3Image();

        articleRepository.deleteById(postId);
        return true;
    }

    public void removeS3Image() {
        List<DeletedUrlPath> deletedUrlPathList = deletedUrlPathRepository.findAll();
        for (DeletedUrlPath deletedUrlPath : deletedUrlPathList) {
            s3Uploader.remove(deletedUrlPath.getDeletedUrlPath());
        }
        deletedUrlPathRepository.deleteAll();
    }
}