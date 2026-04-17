# 커밋 메시지 규칙

## 기본 철학

### 커밋은 "히스토리"다
커밋 메시지는 단순한 작업 내역이 아닌 **의사결정의 기록**입니다.

**코드/주석이 답하는 것:**
- ✅ **무엇을(What)** 했는가?
- ✅ **어떻게(How)** 구현했는가?

**커밋 메시지가 답해야 하는 것:**
- ✅ **왜(Why)** 이 변경이 필요했는가?
- ✅ **무슨 문제(Problem)**를 해결하는가?
- ✅ **왜 이 방법(Decision)**을 선택했는가?
- ✅ **어떤 대안(Alternative)**을 고려했는가?

### 예시: 좋은 커밋 vs 나쁜 커밋

**나쁜 예 (What만 설명):**
```
feat: TwoWayAuthRequired 이벤트 추가

- TwoWayAuthRequired.kt 파일 생성
- DocumentEventType에 TWO_WAY_AUTH_REQUIRED 추가
- Document.requireTwoWayAuth() 메서드 구현
```
→ 코드를 보면 알 수 있는 내용만 나열. **왜** 필요한지 알 수 없음.

**좋은 예 (Why를 설명):**
```
feat: 외부 기관 2차 인증 처리를 위한 이벤트 추가

**문제:**
외부 기관 API 호출 시 2차 인증이 필요한 경우가 있지만,
현재는 이를 처리할 방법이 없어 발급이 실패로 처리됨.

**해결:**
TwoWayAuthRequired 이벤트를 추가하여 2차 인증 필요 상태를 추적.
사용자가 외부에서 인증 완료 후 재시도할 수 있도록 함.

**구현:**
- TwoWayAuthRequired 이벤트 정의
- Document.requireTwoWayAuth(reason) 메서드 추가
- status()에서 TWO_WAY_AUTH_REQUIRED 상태 계산

**대안 고려:**
- 동기 방식으로 2차 인증 대기: 타임아웃 문제로 기각
- 별도 AuthRequired 테이블: Event Sourcing 원칙에 맞지 않아 기각
```
→ 6개월 후에 봐도 **왜 이렇게 했는지** 이해 가능!

---

## 기본 원칙

### 언어
- **한글 사용** (영어 X)
- 명확하고 직관적인 한글 표현
- 기술 용어는 원어 그대로 사용 (Event Sourcing, CQRS 등)

### 구조
```
<타입>: <제목>

<본문>

<꼬리말>
```

---

## 커밋 타입

| 타입 | 설명 | 예시 |
|------|------|------|
| `feat` | 새로운 기능 추가 | `feat: Document 도메인에 파일 업로드 기능 추가` |
| `fix` | 버그 수정 | `fix: 2차 인증 상태 전환 오류 수정` |
| `docs` | 문서 수정 | `docs: Event Sourcing 아키텍처 문서 추가` |
| `refactor` | 코드 리팩토링 | `refactor: Document 상태 계산 로직 개선` |
| `test` | 테스트 추가/수정 | `test: 서류 업로드 시나리오 테스트 추가` |
| `chore` | 빌드, 설정 변경 | `chore: Kotest 의존성 추가` |
| `style` | 코드 포맷팅 | `style: 파일 끝 newline 추가` |

---

## 제목 작성 규칙

### 형식
- **50자 이내**
- **마침표 없음**
- **명령형** 사용 ("추가한다" X, "추가" O)

### 좋은 예시
```
feat: TwoWayAuthRequired 이벤트 추가
fix: status() 메서드에서 null 처리 누락 수정
docs: 서류 발급 플로우 문서 작성
test: 2차 인증 요구 시나리오 테스트 추가
refactor: replay 패턴으로 Event Sourcing 방식 변경
chore: build.gradle.kts에 Kotest 라이브러리 추가
```

### 나쁜 예시
```
❌ Add TwoWayAuthRequired event (영어)
❌ 버그를 수정했습니다. (과거형)
❌ 문서 업데이트 및 테스트 추가와 코드 리팩토링 (여러 작업 혼재)
❌ fix (제목 없음)
```

---

## 본문 작성 규칙

### 언제 작성하는가?
- **단순 변경**: 본문 생략 가능
- **복잡한 변경**: 본문 필수
  - 왜 이 변경이 필요한가?
  - 무엇을 변경했는가?
  - 어떤 이슈가 해결되었는가?

### 형식
- 제목과 본문 사이 **빈 줄** 필수
- 한 줄당 **72자 이내**
- **불릿 포인트** 사용 가능 (-, *, +)

### 예시: 문제-해결-구현 구조
```
feat: Document 도메인에 2차 인증 처리 추가

**문제:**
외부 기관 API 호출 시 2차 인증이 필요한 경우가 발생하지만,
현재는 이를 처리할 방법이 없어 발급이 실패로 처리됨.

**해결:**
TwoWayAuthRequired 이벤트를 추가하여 2차 인증 필요 상태를 추적.
사용자가 외부에서 인증 완료 후 재시도할 수 있도록 함.

**구현:**
- TwoWayAuthRequired 이벤트 정의
- Document.requireTwoWayAuth(reason) 메서드 구현
- status() 메서드에서 TWO_WAY_AUTH_REQUIRED 상태 계산
- BDD 스타일 테스트 작성

외부 기관에서 2차 인증을 요구하면 상태가 TWO_WAY_AUTH_REQUIRED로
변경되며, 이벤트는 append-only로 저장됩니다.

**대안 검토:**
- 동기 방식 대기: 타임아웃 문제로 기각
- 별도 테이블: Event Sourcing 원칙에 맞지 않아 기각
```

---

## 꼬리말 작성 규칙

### Co-Authored-By (필수)
Claude Code로 작성한 커밋에는 반드시 포함:
```
🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### 이슈 참조 (선택)
```
Resolves: #123
Ref: #456
Related to: #789
```

---

## 실전 예시

### 예시 1: 기능 추가
```
feat: 유저 직접 서류 업로드 기능 추가

사용자가 외부 기관 API를 사용하지 않고 직접 서류를 업로드할 수 있는
기능을 추가했습니다.

- DocumentUploaded 이벤트 추가
- Document.uploadFile(fileUrl) 메서드 구현
- DocumentEventType.DOCUMENT_UPLOADED 추가
- status()에서 DocumentUploaded 처리 시 COMPLETED 상태로 변경
- "유저가 직접 서류를 첨부하면" 테스트 시나리오 추가

유저가 파일을 업로드하면 DocumentUploaded 이벤트가 발행되고,
상태가 즉시 COMPLETED로 변경됩니다.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### 예시 2: 문서 추가
```
docs: 외부 API 비동기 처리 전략 문서화

외부 기관 API의 특이한 동작 방식과 해결 전략을 문서화했습니다.

**문제:**
- 외부 기관 API는 성공 시 응답을 물고 있음(hanging)
- 실패/2차 인증은 즉시 응답

**해결 방안:**
- 타임아웃 기반 성공 판단 (2-3초)
- 타임아웃 시 = 성공으로 간주
- 즉시 응답 = 실패 또는 2차 인증 필요

**비동기 처리 전략:**
1. 이벤트 기반 비동기 처리 (추후 고려)
2. 타임아웃 + 백프레셔 (현재 채택)

멀티스레딩의 한계(Thread Pool 고갈, 메모리 부족)와
해결 방안을 상세히 기록했습니다.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### 예시 3: 테스트 추가
```
test: BDD 스타일 DocumentTest 추가

Kotest DescribeSpec을 사용한 행위 중심 테스트 작성:
- "서류 발급을 요청하면" 시나리오
- DocumentRequested 이벤트 발행 검증
- 상태가 REQUESTED인지 검증

한글 describe/it 구조로 비즈니스 요구사항을 명확히 표현합니다.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### 예시 4: 의존성 추가
```
chore: Kotest 의존성 추가

BDD 스타일 테스트 작성을 위해 Kotest 라이브러리 추가:
- kotest-runner-junit5:5.9.1
- kotest-assertions-core:5.9.1

describe/it 구조로 행위 중심 테스트를 작성할 수 있습니다.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 주의사항

### 하지 말아야 할 것
1. ❌ 여러 작업을 하나의 커밋에 섞지 않기
   ```
   ❌ feat: 기능 추가 및 버그 수정 및 문서 업데이트
   ```

2. ❌ 모호한 메시지 사용하지 않기
   ```
   ❌ fix: 버그 수정
   ❌ chore: 업데이트
   ❌ refactor: 코드 개선
   ```

3. ❌ 너무 긴 제목
   ```
   ❌ feat: Document 도메인에 사용자가 직접 서류를 업로드할 수 있는 기능을 추가하고 관련 테스트도 작성함
   ```

### 해야 할 것
1. ✅ 한 커밋 = 한 가지 목적
2. ✅ 구체적이고 명확한 제목
3. ✅ 변경 이유와 맥락 제공 (본문)
4. ✅ 테스트 완료 후 커밋

---

## 커밋 수정하기

### 마지막 커밋 메시지 수정
```bash
git commit --amend
```

### 여러 커밋 메시지 수정 (rebase)
```bash
git rebase -i HEAD~3  # 최근 3개 커밋 수정
```

### 커밋 메시지만 수정 (파일 변경 없음)
```bash
git commit --amend --only -m "새로운 커밋 메시지"
```

---

---

## 좋은 커밋 메시지의 가치

### 미래의 나를 위한 기록
6개월 후, 1년 후:
- "이 코드 왜 이렇게 짰지?" → 커밋 메시지 확인
- "이 버그 왜 발생했지?" → `git blame`으로 커밋 추적
- "이 기능 왜 이렇게 설계했지?" → 커밋 히스토리 확인

### 팀을 위한 문서
- 코드 리뷰 시 맥락 이해
- 신규 팀원 온보딩
- 장애 원인 추적

### 포트폴리오로서의 커밋 히스토리
- 기술 면접 시 설명 자료
- 문제 해결 능력 증명
- 의사결정 과정 시연

**예시 시나리오:**
```
면접관: "Event Sourcing을 왜 선택했나요?"
지원자: "커밋 히스토리를 보시면..."
  → docs: Event Sourcing vs CQRS 비교 문서
  → "실무에서 사용한 replay 패턴을 채택했고..."
  → "uncommittedEvents 방식과 비교하여..."
```

---

## 참고 자료

- [Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/)
- [Git Commit Message Style Guide](https://github.com/angular/angular/blob/main/CONTRIBUTING.md#commit)
- [How to Write a Git Commit Message](https://cbea.ms/git-commit/)
- 프로젝트 특성에 맞게 한글화 및 커스터마이징