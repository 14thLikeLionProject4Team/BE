package com.cheotdanchu.service;

import com.cheotdanchu.ai.SeedRepository;
import com.cheotdanchu.dto.AccountDtos;
import com.cheotdanchu.entity.Account;
import com.cheotdanchu.entity.Member;
import com.cheotdanchu.repository.AccountRepository;
import com.cheotdanchu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 연결 계좌 화면(마이): 자녀·부모 공용 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final SeedRepository seedRepository;

    /**
     * 우리 가족의 계좌(자녀 → 부모 순)를 마스킹한 계좌번호와 함께 준다. 잔액은 호출한 사람 본인 계좌만 주고 상대 계좌는 null.
     * 잔액은 계좌 전체 잔액이라 지난달까지 남은 돈을 포함한다(홈의 '이번 달 쓸 수 있는 돈'과 다른 값).
     * 자녀가 만 19세가 되면 그 기록은 본인만 보므로, 부모에게는 자녀 계좌를 빼고 부모 본인 계좌만 준다.
     */
    public AccountDtos.ListResponse getAccounts(Long personaId) {
        Member me = memberRepository.getPersona(personaId);
        Long familyId = me.getFamily().getFamilyId();
        LocalDate today = seedRepository.today();

        List<AccountDtos.Item> items = new ArrayList<>();
        for (Member.Role role : List.of(Member.Role.CHILD, Member.Role.PARENT)) {
            memberRepository.findByFamily_FamilyIdAndRole(familyId, role)
                    .filter(owner -> isVisibleTo(owner, me, today))
                    .ifPresent(owner -> accountRepository.findByMemberId(owner.getMemberId())
                            .ifPresent(account -> items.add(item(owner, account, me))));
        }
        return new AccountDtos.ListResponse(items);
    }

    private static AccountDtos.Item item(Member owner, Account account, Member me) {
        boolean mine = owner.getMemberId().equals(me.getMemberId());
        return new AccountDtos.Item(account.getAccountId(), owner.getRole(), account.getBankName(),
                AccountNumberMask.mask(account.getAccountNumber()), mine ? account.getBalance() : null);
    }

    /** 부모는 만 19세가 된 자녀의 계좌를 보지 못한다. 그 밖에는 가족 모두 서로의 계좌(마스킹)를 본다 */
    private static boolean isVisibleTo(Member owner, Member viewer, LocalDate today) {
        return !(viewer.getRole() == Member.Role.PARENT && owner.getRole() == Member.Role.CHILD && owner.isAdultOn(today));
    }
}
