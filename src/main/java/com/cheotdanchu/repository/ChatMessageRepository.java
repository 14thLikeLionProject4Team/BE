package com.cheotdanchu.repository;

import com.cheotdanchu.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 내 대화를 최신순으로. cursor는 이전 페이지 마지막 messageId */
    List<ChatMessage> findByMemberIdAndMessageIdLessThanOrderByMessageIdDesc(Long memberId, Long cursor, Pageable pageable);
}
