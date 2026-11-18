package com.cheotdanchu.repository;

import com.cheotdanchu.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 내 대화를 최신순으로. cursor는 이전 페이지 마지막 messageId */
    List<ChatMessage> findByMemberIdAndMessageIdLessThanOrderByMessageIdDesc(Long memberId, Long cursor, Pageable pageable);

    /** 내 대화를 모두 지운다. 이 대화에서 나온 전할 말은 DB(ON DELETE SET NULL)가 출처 연결만 끊는다 */
    @Modifying(clearAutomatically = true)
    @Query("delete from ChatMessage m where m.memberId = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);
}
