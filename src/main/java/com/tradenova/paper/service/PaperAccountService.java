package com.tradenova.paper.service;

import com.tradenova.paper.dto.BaseCurrency;
import com.tradenova.paper.dto.PaperAccountCreateRequest;
import com.tradenova.paper.dto.PaperAccountResponse;
import com.tradenova.paper.dto.PaperAccountUpdateRequest;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaperAccountService {

    //계좌 생성/조회/수정
    private final PaperAccountRepository paperAccountRepository;
    //계좌 리셋 시 포지션 정리
    private final PaperPositionRepository paperPositionRepository;
    //계좌 생성 시 유저 존재 검증
    private final UserRepository userRepository;

    @Transactional //트랜잭션 등록
    public PaperAccountResponse create(Long userId, PaperAccountCreateRequest req){
        //유저 존재 검증 없으면 예외
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found!"));

        //초기 자본금 처리, 초기 자본 미입력 -> 0원
        BigDecimal initial = (req.initialBalance() == null) ? BigDecimal.ZERO : req.initialBalance();
        //기본 계좌 여부 결정, 이 유저의 첫 번째 계좌면 자동 default (이미 있으면 false)
        boolean shouldDefault = paperAccountRepository.findByUserIdAndIsDefaultTrue(userId).isEmpty();

        //계좌 생성 & 저장
        PaperAccount saved = paperAccountRepository.save(
                PaperAccount.builder()
                        .user(user)
                        .name((req.name() == null || req.name().isBlank()) ? "기본 계좌" : req.name().trim())
                        .description(req.description())
                        .initialBalance(initial)
                        .cashBalance(initial)
                        .baseCurrency(BaseCurrency.KRW)
                        .isDefault(shouldDefault)
                        .build()
        );
        //Response DTO로 변환
        return toResponse(saved);
    }

    //이 유저가 가진 계좌 최신 순서로 조회
    @Transactional(readOnly = true) //readOnly 트랜잭션으로 성능 최적화
    public List<PaperAccountResponse> list(Long userId) {
        return paperAccountRepository.findAllByUserIdOrderByIdDesc(userId)
                .stream().map(this::toResponse)
                .toList();
    }

    //기본 계좌 변경, 이 유저의 기본 계좌를 하나로 지정
    @Transactional
    public void setDefault(Long userId, Long accountId) {
        //다른 유저 계좌 접근 방지
        PaperAccount target = paperAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new IllegalArgumentException("PAPER_ACCOUNT_NOT_FOUND"));
        //기존 기본 계좌 해제
        paperAccountRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(acc -> acc.setDefault(false));
        //새 계좌를 default로
        target.setDefault(true);
    }

    //계좌 리셋
    @Transactional
    public void reset(Long userId, Long accountId) {
        //계좌 소유권 검증
        PaperAccount acc = paperAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new IllegalArgumentException("PAPER_ACCOUNT_NOT_FOUND"));

        // 1) 현금 리셋
        acc.resetCash();

        // 2) 포지션 전부 삭제
        paperPositionRepository.deleteAllByAccountId(accountId);
    }

    @Transactional
    public PaperAccountResponse update(Long userId, Long accountId, PaperAccountUpdateRequest req) {
        //본인 계좌가 맞는지 조회
        PaperAccount acc = paperAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new IllegalArgumentException("PAPER_ACCOUNT_NOT_FOUND"));

        //값이 있을 때만 업데이트
        if (req.name() != null && !req.name().isBlank()) {
            acc.setName(req.name().trim());
        }
        //null만 아니면 업데이트
        if (req.description() != null) {
            acc.setDescription(req.description().trim());
        }

        // JPA dirty checking으로 자동 반영됨 (save 안 해도 됨)
        return toResponse(acc);
    }


    //Entity → DTO 변환, “외부로 노출할 계좌 정보만 골라서 전달”
    private PaperAccountResponse toResponse(PaperAccount a) {
        return new PaperAccountResponse(
                a.getId(),
                a.getName(),
                a.getDescription(),
                a.getInitialBalance(),
                a.getCashBalance(),
                a.getBaseCurrency(),
                a.isDefault(),
                a.getCreatedAt()
        );
    }

}
