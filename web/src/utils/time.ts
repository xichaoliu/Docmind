// utils/time.ts
import dayjs from 'dayjs'
import calendar from 'dayjs/plugin/calendar'

dayjs.extend(calendar)

export function formatTime(time: string | Date) {
  return dayjs(time).calendar(null, {
    sameDay: '[今天] HH:mm',
    nextDay: '[明天] HH:mm',
    nextWeek: 'MM-DD HH:mm',
    lastDay: '[昨天] HH:mm',
    lastWeek: 'MM-DD HH:mm',
    sameElse: 'YYYY-MM-DD',
  })
}