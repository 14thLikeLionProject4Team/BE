package com.cheotdanchu.repository;

import com.cheotdanchu.entity.SubmissionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SubmissionPhotoRepository extends JpaRepository<SubmissionPhoto, Long> {

    List<SubmissionPhoto> findBySubmission_SubmissionIdInOrderByPhotoId(Collection<Long> submissionIds);
}
