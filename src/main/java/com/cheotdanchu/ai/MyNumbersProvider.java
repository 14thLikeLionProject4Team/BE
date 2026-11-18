package com.cheotdanchu.ai;

/**
 * "내숫자" 질문(이번 달 얼마 썼어? 등)에 원장 숫자로 답하는 곳이에요. LLM을 쓰지 않아요.
 *
 * 백엔드가 이 인터페이스를 구현한 @Component를 하나 만들면 그걸 써요.
 * 없으면 "내 금액은 홈 화면에서 확인할 수 있어요."로 답해요.
 */
public interface MyNumbersProvider {
    String answer(String personaId, String question);
}
