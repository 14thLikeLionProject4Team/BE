# BE

## 로컬 실행 주의

- `spring.sql.init.mode`는 `always`(기본)로 둡니다. 서버를 켤 때마다 `schema.sql`이 테이블을 새로 만들고, `data.sql`과 `SeedLoader`가 시드 데이터를 넣습니다.
- `never`로 바꾸지 않습니다. 테이블이 남은 채로 켜면 `SeedLoader`가 이미 있는 데이터에 다시 넣으려다 중복 키 오류로 서버가 시작되지 않습니다.
  - 스키마가 바뀐 뒤에는 `never`인 DB가 `schema.sql`·`data.sql`을 받지 못해 어차피 낡은 상태가 됩니다.
  - 꼭 써야 한다면 DB의 테이블을 직접 지우고 켜세요.
- 테스트(`./gradlew test`)도 같은 개발 DB를 쓰고 서버를 켤 때처럼 테이블을 새로 만들기 때문에, 실행하면 DB가 초기화됩니다.
