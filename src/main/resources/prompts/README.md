# 첫단추 AI 프롬프트

`src/main/resources/prompts/`에 이 폴더의 파일을 그대로 넣으면 돼요.
프롬프트 문장은 AI 담당(민규)이 고치고, 백엔드는 파일을 읽어서 빈칸만 채워 보내요.

## 1. 조립 방법

LLM에 보내는 system 메시지 = `_common.md` + 빈 줄 + 각 프롬프트 파일(빈칸 채운 것)

```java
String system = prompts.load("_common") + "\n\n"
        + prompts.load("rag_answer").replace("{cards}", cardsText);
String raw = llm.call(system, userMessage);
```

- 빈칸은 `{이름}` 모양이에요. `String.replace`로 바꾸면 돼요.
- 모든 프롬프트는 **JSON 한 개**로 답하게 되어 있어요. 파싱에 실패하면 아래 표의 "실패하면"대로 처리해요.
- 같은 모델, 같은 API 키를 쓰고 프롬프트만 바꿔요.

## 2. 파일별 정리

| 파일 | 언제 부르나 | 빈칸 | user 메시지 | 돌려받는 JSON | 실패하면 |
|---|---|---|---|---|---|
| `classify.md` | 대화창 메시지마다 (위기 검사 통과 후) | 없음 | 아이 메시지 | `{"kind"}` | `"금융지식"`으로 보고 RAG로 |
| `rag_answer.md` | kind = 금융지식 | `{cards}` | 아이 질문 | `{"answer","used_ids"}` | 1등 카드의 `body`를 그대로 보여주기 |
| `worry_chat.md` | kind = 고민 | `{child_name}` | 이전 대화 + 아이 메시지 | `{"reply","danger"}` | "이야기해 줘서 고마워요. 조금 더 말해 줄래요?" |
| `memo_extract.md` | 고민 대화를 나갈 때 | `{conversation}` | "메모를 정리해 줘" | `{"memos":[...]}` | 메모 없음으로 처리 |
| `message_draft.md` | 월말 전할 말 승인 화면을 열 때 | `{child_name}`, `{memos}` | "초안을 써 줘" | `{"drafts":[...]}` | 메모의 `요청`을 그대로 초안으로 |
| `monthly_review.md` | 결제일 정산 후 (시연은 화면을 열 때 만들고 저장) | `{facts}` | "회고를 써 줘" | `{"headline","body","next_tip"}` | 틀 문장 |
| `monthly_briefing.md` | 월 1회 (시연은 화면을 열 때 만들고 저장) | `{facts}` | "브리핑을 써 줘" | `{"headline","body","suggestion"}` | 틀 문장 |
| `decision_card.md` | 용돈 제안이 생길 때 | `{facts}` | "카드를 써 줘" | `{"title","summary","reasons","note"}` | 틀 문장 |

kind = 내숫자는 LLM을 부르지 않고 원장에서 계산해서 틀 문장으로 답해요. kind = 범위밖은 "그건 내가 도와주기 어려워요."로 바로 답해요.

## 3. 빈칸에 넣는 모양

### `{cards}` (rag_answer)

검색 점수 기준을 넘은 카드만 최대 3개. **기준을 넘는 카드가 하나도 없으면 LLM을 부르지 말고** "그건 내가 가진 자료로는 정확히 답하기 어려워요."를 바로 보내요.

```
(1) id: svc_shortfall | 종류: service
제목: 부족하면 어떻게 돼?
내용: 결제일에 내 계좌 돈이 청구액보다 적으면 ...
예시: 청구액 50,000원, 내 계좌에 45,000원이 있으면 ...
화면: 자녀 홈 > 결제일 전 점검
출처: 첫단추 서비스 안내
```

`aliases`, `priority`는 검색에만 쓰고 넣지 않아요.

### `{facts}` (monthly_review, monthly_briefing, decision_card)

원장에서 계산한 값을 JSON으로 넣어요. **금액은 "61,500원"처럼 글자로 만들어서** 넣어요. LLM이 숫자를 고쳐 쓰지 않게 하려는 거예요. 항목 이름은 각 프롬프트의 "[사실 항목 설명]"과 같게 맞춰요.

**monthly_briefing에는 부모님이 볼 수 있는 값만 넣어요.** 사용처, 항목별 금액, 결제일 전 점검(D-6), 대화 내용은 백엔드에서 빼고 넣어요. 프롬프트에도 쓰지 말라고 했지만, 애초에 안 넣는 게 제일 확실해요.

### `{conversation}` (memo_extract)

```
아이: ...
첫단추: ...
아이: ...
```

### `{memos}` (message_draft)

memo_extract가 돌려준 `memos` 배열을 그대로 넣어요. 상태가 "해결됨", "전하지 않기를 원함"인 것은 빼고 넣어도 돼요.

## 4. 화면에 보여주기 전 검사

LLM 답을 그대로 보여주지 말고 아래를 확인해요. 하나라도 걸리면 "실패하면"대로 처리해요.

| 검사 | 대상 |
|---|---|
| JSON 파싱이 되나, 필요한 키가 다 있나 | 전부 |
| `used_ids`가 넣어준 카드 id 안에 있나 | rag_answer |
| 넣어준 카드에 `safety`가 있으면 답에 그 카드의 연락처 번호가 들어 있나 | rag_answer |
| 답에 나온 금액이 모두 `{facts}`에 있는 금액인가 | monthly_review, monthly_briefing, decision_card |
| 부모용 글에 가게 이름, 항목 이름이 없나 | monthly_briefing |
| 금지어가 없나 (아래 목록) | 전부 |

### 금지어 시작 목록

```
잘못, 실패, 나쁜 습관, 낭비, 혼나, 한심,
대출 받아, 빌려 봐, 빌리면 돼, 대리입금 해, 현금서비스 받아,
투자해, 사 두세요, 추천해요,
카드가 나와요, 카드가 발급돼요, 신용점수가 올라
```

평가셋을 돌리면서 늘려 가요.

## 5. 위기 신호

1. 대화창 메시지는 **분류보다 먼저** `SafetyService.isCrisis()`(키워드 규칙)로 검사해요.
2. worry_chat이 `"danger": true`를 돌려줘도 같은 위기 안내로 바꿔요.
3. 위기 안내는 LLM이 쓰지 않고 고정 문장을 써요. 부모님께는 알리지 않아요.

고정 문장 예:
```
지금 많이 힘든 것 같아요. 혼자 견디지 않아도 돼요.
청소년상담 1388(전화·문자·카카오톡)에서 지금 바로 이야기를 들어줄 수 있어요.
위험한 상황이면 112에 연락하세요.
```

## 6. MVP에서 줄이려면

- `memo_extract` + `message_draft` 합치기: memo_extract의 메모 항목에 `"초안"`을 추가
- `decision_card`는 틀 문장으로: 시연에서 결정 카드는 가족 A의 73,000원 제안 하나뿐이에요

그러면 6개(classify, rag_answer, worry_chat, memo_extract, monthly_review, monthly_briefing)예요.

## 7. 아직 없는 자료

`rag_answer.md` 예시에 나오는 `term_installment`(할부)는 아직 RAG 자료에 없어요. 신용카드, 할부, 리볼빙, 현금서비스, 연체, 신용점수 카드를 추가해야 해요.
