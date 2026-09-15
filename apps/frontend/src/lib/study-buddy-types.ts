export type StudyMode = 'IN_PERSON' | 'ONLINE' | 'EITHER';
export type StudyArrangement = 'ONE_TO_ONE' | 'SMALL_GROUP' | 'EITHER';
export type StudyGoal =
  | 'CONCEPT_REVIEW'
  | 'PROBLEM_SOLVING'
  | 'EXAM_PREPARATION'
  | 'PROJECT_DISCUSSION';
export type RequestStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'ENDED';
export interface Course {
  code: string;
  title: string;
}
export interface AvailabilitySlot {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
}
export interface StudyPreference {
  courseCode: string;
  preferredMode: StudyMode;
  preferredArrangement: StudyArrangement;
  studyGoal: StudyGoal;
  preferredGroupSize: number;
  availability: AvailabilitySlot[];
}
export interface StudentProfile {
  id: string;
  name: string;
  school: string;
  programme: string;
  yearOfStudy: number;
  contactNumber?: string | null;
  currentCourses: string[];
  preference: StudyPreference;
}
export interface MatchBreakdown {
  courseScore: number;
  availabilityScore: number;
  studyModeScore: number;
  studyGoalScore: number;
  groupSizeScore: number;
}
export interface MatchResult {
  student: StudentProfile;
  score: number;
  breakdown: MatchBreakdown;
  explanation: string;
}
export interface StudyGroup {
  id?: string;
  courseCode: string;
  name: string;
  description: string;
  studyGoal: StudyGoal;
  preferredMode: StudyMode;
  availability: AvailabilitySlot[];
  maximumGroupSize: number;
  leaderId: string;
  memberIds: string[];
  status?: 'ACTIVE' | 'CLOSED';
}
export interface MatchingConfig {
  strategy: 'BALANCED' | 'AVAILABILITY_FIRST' | 'COURSE_FIRST';
  courseWeight: number;
  availabilityWeight: number;
  studyModeWeight: number;
  studyGoalWeight: number;
  groupSizeWeight: number;
  maxResults: number;
}
export interface Account {
  id: string;
  name: string;
  role: 'STUDENT' | 'SYSTEM_ADMINISTRATOR';
  status?: 'ACTIVE' | 'SUSPENDED';
  createdAt?: string;
  lastActiveAt?: string | null;
}
export interface AccountUsage {
  account: Account;
  profileComplete: boolean;
  activeConnections: number;
  groupsJoined: number;
  pendingRequests: number;
}
export interface BuddyRequest {
  id: string;
  senderId: string;
  receiverId: string;
  message?: string;
  status: RequestStatus;
  createdAt: string;
}
export interface MembershipRequest {
  id: string;
  groupId: string;
  studentId: string;
  status: RequestStatus;
  createdAt: string;
}
export const modes: StudyMode[] = ['IN_PERSON', 'ONLINE', 'EITHER'];
export const arrangements: StudyArrangement[] = [
  'ONE_TO_ONE',
  'SMALL_GROUP',
  'EITHER',
];
export const goals: StudyGoal[] = [
  'CONCEPT_REVIEW',
  'PROBLEM_SOLVING',
  'EXAM_PREPARATION',
  'PROJECT_DISCUSSION',
];
export const days = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];
export const label = (value: string) =>
  value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/^./, (s) => s.toUpperCase());
