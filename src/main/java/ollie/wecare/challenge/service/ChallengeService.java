package ollie.wecare.challenge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ollie.wecare.challenge.dto.*;
import ollie.wecare.challenge.entity.Challenge;
import ollie.wecare.challenge.entity.ChallengeAttendance;
import ollie.wecare.challenge.repository.ChallengeAttendanceRepository;
import ollie.wecare.challenge.repository.ChallengeRepository;
import ollie.wecare.common.base.BaseException;
import ollie.wecare.common.base.BaseResponse;
import ollie.wecare.common.enums.Role;
import ollie.wecare.program.dto.DateDto;
import ollie.wecare.user.entity.User;
import ollie.wecare.user.repository.UserRepository;
import ollie.wecare.user.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;


import static ollie.wecare.common.base.BaseResponseStatus.*;
import static ollie.wecare.common.constants.Constants.ACTIVE;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChallengeService {

    private final ChallengeAttendanceRepository challengeAttendanceRepository;
    private final UserRepository userRepository;
    private final ChallengeRepository challengeRepository;

    private final UserService userService;

    // 참여중인 챌린지 목록 조회
    public BaseResponse<List<GetChallengesRes>> getMyChallenges(Long userIdx) throws BaseException {
        User user = userRepository.findByUserIdxAndStatusEquals(userIdx, ACTIVE).orElseThrow(() -> new BaseException(INVALID_USER_IDX));
        if(user.getRole().equals(Role.Challenger)){
            return new BaseResponse<>(challengeRepository.findByParticipantsContaining(user).stream()
                    .map(challenge -> GetChallengesRes.fromChallenge(challenge, calculateMyAchievementRate(user, challenge)))
                    .distinct()
                    .toList());
        }
        else { // 관리자
            return new BaseResponse<>(challengeRepository.findByAdmin(user).stream()
                    .map(challenge -> GetChallengesRes.fromChallenge(challenge, calculateMyAchievementRate(user, challenge)))
                    .distinct()
                    .toList());
        }

    }

    public BaseResponse<List<GetChallengesAdminRes>> getMyChallengesAdmin(Long userIdx) throws BaseException {
        User user = userRepository.findByUserIdxAndStatusEquals(userIdx, ACTIVE).orElseThrow(() -> new BaseException(INVALID_USER_IDX));
        if(user.getRole().equals(Role.Admin)){
            return new BaseResponse<>(challengeRepository.findByAdmin(user).stream()
                    .map(GetChallengesAdminRes::fromChallenge)
                    .distinct()
                    .toList());
        }
        else throw new BaseException(INVALID_ROLE);
    }

    public BaseResponse<List<GetChallengeAdminRes>> getMyChallengeAdmin(Long userIdx, Long challengeIdx) {
        Challenge challenge = challengeRepository.findById(challengeIdx).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX));
        if(!challenge.getAdmin().getUserIdx().equals(userIdx)) throw new BaseException(INVALID_ROLE);
        return new BaseResponse<>(challenge.getParticipants().stream().map(participant -> GetChallengeAdminRes
                .fromParticipant(participant, challengeAttendanceRepository.countByUserAndChallenge(participant, challenge))).toList());
    }

    /*
     * 챌린지 인증코드 발급
     * */
    public GetAttendanceCodeReq getAttendanceCode(Long challengeIdx) {

        User user = userService.getUserWithValidation();
        if(!user.getRole().equals(Role.Admin)) throw new BaseException(INVALID_ROLE);

        Challenge challenge = challengeRepository.findById(challengeIdx).orElseThrow(() -> new BaseException(INVALID_CHALLENGE_IDX));

        Random random = new Random();
        String code = "";
        for (int i=0; i<6; i++) code += Integer.toString(random.nextInt(9));

        challenge.updateAttendanceCode(code);
        return GetAttendanceCodeReq.builder().code(code).build();
    }

    /*
     * 챌린지 인증
     * */
    public void attendChallenge(AttendChallengeReq attendChallengeReq) throws BaseException {
        Challenge challenge = challengeRepository.findById(attendChallengeReq.getChallengeIdx()).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX));
        if(!challenge.getAttendanceCode().equals(attendChallengeReq.getAttendanceCode()))
            throw new BaseException(INVALID_ATTENDANCE_CODE);
        else {
            ChallengeAttendance challengeAttendance = ChallengeAttendance.builder()
                    .user(userService.getUserWithValidation())
                    .challenge(challengeRepository.findById(attendChallengeReq.getChallengeIdx()).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX)))
                    .build();
            challengeAttendanceRepository.save(challengeAttendance);
            Integer newAttendanceRate = (int) ((challenge.getAttendanceRate()/(float)100 + (1/(float)((challenge.getTotalNum()) * challenge.getParticipants().size()))) * 100);
            challenge.updateAttendanceRate(newAttendanceRate);
        }
    }

    /*
     * 새로운 챌린지 참여
     * */
    public void participateChallenge(PostChallengeReq postChallengeReq) throws BaseException {
        Challenge challenge = challengeRepository.findById(postChallengeReq.getChallengeIdx()).orElseThrow(()-> new BaseException(INVALID_CHALLENGE_IDX));
        challenge.setParticipants(userService.getUserWithValidation());
    }

    /*
     * 챌린지 검색
     * */
    public List<SearchChallengeRes> getChallenges(String searchWord) {
        User user = userService.getUserWithValidation();
        if (searchWord.equals("")) {
            return challengeRepository.findAll().stream()
                    .map(SearchChallengeRes::fromChallenge)
                    .toList();
        } else {
            List<Challenge> challenges = challengeRepository.findByNameContainingAndParticipantsNotContaining(searchWord, user);
            return challenges.stream()
                    .distinct()
                    .map(SearchChallengeRes::fromChallenge)
                    .toList();
        }
    }

    // 챌린지 상세 조회
    public BaseResponse<ChallengeDetailResponse> getChallengeDetail(Long userIdx, Long challengeIdx) {
        User user = userRepository.findByUserIdxAndStatusEquals(userIdx, ACTIVE).orElseThrow(() -> new BaseException(INVALID_USER_IDX));
        Challenge challenge = challengeRepository.findByChallengeIdxAndStatusEquals(challengeIdx, ACTIVE).orElseThrow(() -> new BaseException(INVALID_CHALLENGE_IDX));
        if (!challenge.getParticipants().contains(user)) throw new BaseException(NO_PARTICIPANT);

        List<DateDto> attendanceList = challengeAttendanceRepository.findByUserAndChallengeOrderByCreatedDate(user, challenge).stream()
                .map(challengeAttendance -> convertToDateDto(challengeAttendance.getCreatedDate()))
                .toList();

        ChallengeDetailResponse challengeDetail = new ChallengeDetailResponse(challenge.getName(), challenge.getParticipants().size(),
                attendanceList, challenge.getAttendanceRate(), calculateMyAchievementRate(user, challenge));
        return new BaseResponse<>(challengeDetail);
    }

    // DateDto 형태 변환
    private static DateDto convertToDateDto(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return new DateDto(
                dateTime.getYear(),
                dateTime.getMonthValue(),
                dateTime.getDayOfMonth());
    }

    // 챌린지 개인 달성률 계산
    private Integer calculateMyAchievementRate(User user, Challenge challenge) {
        Integer attendanceCount = challengeAttendanceRepository.countByUserAndChallenge(user, challenge);
        if (attendanceCount == null) return 0;
        else return (int)((attendanceCount/(float)challenge.getTotalNum()) * 100);
    }

    /*
    * 챌린지 배너 조회 (홈화면)
    * */
    public GetChallengeAdsRes getChallengeAds() {

        Challenge mostAttendancedChallenge = challengeRepository.findTop1ByOrderByAttendanceRateDesc().orElseThrow(()-> new BaseException(NO_CHALLENGE));
        Challenge mostParticipatedChallenge = challengeRepository.findMostParticipatedChallenge(PageRequest.of(0, 1)).getContent().get(0);
        Challenge mostRecentlyStartedChallenge = challengeRepository.findTop1ByOrderByCreatedDateDesc().orElseThrow(()-> new BaseException(NO_CHALLENGE));


        return GetChallengeAdsRes.builder()
                .mostAttendancedChallenge(GetChallengeAdRes.fromChallenge(mostAttendancedChallenge))
                .mostParticipatedChallenge(GetChallengeAdRes.fromChallenge(mostParticipatedChallenge))
                .mostRecentlyStartedChallenge(GetChallengeAdRes.fromChallenge(mostRecentlyStartedChallenge)).build();
    }
}
