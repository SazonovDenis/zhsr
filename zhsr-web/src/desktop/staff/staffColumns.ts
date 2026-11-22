import type { StaffRow } from '@/api/staff'
import { t } from '@/boot/i18n'
import { defineColumns } from '@/shared/components/table/types'
import CellStaffFact from './cells/CellStaffFact.vue'
import CellStaffName from './cells/CellStaffName.vue'
import CellStaffPlan from './cells/CellStaffPlan.vue'
import CellStaffRoles from './cells/CellStaffRoles.vue'

/** Колонки таблицы «Сотрудники»; логин — под ФИО (сервер отдаёт его только администратору). */
export function staffColumns() {
  return defineColumns<StaffRow>([
    { field: 'fullName', title: 'ФИО', width: 'minmax(12rem, 1.4fr)', sortable: true, cell: CellStaffName },
    { field: 'roles', title: 'Роль и категория', width: 'minmax(10rem, 1.2fr)', cell: CellStaffRoles },
    {
      field: 'factTrip',
      title: 'Статус',
      width: 'minmax(min-content, 0.5fr)',
      sortable: true,
      format: (v) => (v ? t('В рейсе') : t('Свободен')),
    },
    { field: 'station', title: 'Местонахождение', width: 'minmax(18rem, 2.1fr)', sortable: true, cell: CellStaffFact },
    { field: 'planTrip', title: 'Назначен', width: 'minmax(18rem, 2.1fr)', cell: CellStaffPlan },
  ])
}
