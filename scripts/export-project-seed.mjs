import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'

const backend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const frontend = path.resolve(process.argv[2] || path.join(backend, '..', 'personal-website'))
const site = fs.readFileSync(path.join(frontend, 'src/content/siteContent.js'), 'utf8')
const projectsLiteral = site.split('export const fallbackProjects = ')[1].split('\n\nexport const siteContent')[0]
const translationsLiteral = site.split('const projectTranslations = ')[1].split('\n\nexport function localizeProjects')[0]
const fallback = vm.runInNewContext(`(${projectsLiteral})`, {}, { timeout: 1000 })
const translations = vm.runInNewContext(`(${translationsLiteral})`, {}, { timeout: 1000 })
const copySource = fs.readFileSync(path.join(frontend, 'src/content/portfolioCopy.js'), 'utf8')
const copy = vm.runInNewContext(`(${copySource.replace('export const portfolioCopy = ', '')})`, {}, { timeout: 1000 })
const configured = JSON.parse(fs.readFileSync(path.join(frontend, 'public/config/projects.json'), 'utf8')).projects
const order = [1, 3, 5, ...fallback.filter(project => ![1, 3, 5].includes(project.id)).map(project => project.id)]
const projects = fallback.map(project => {
  const value = { ...project, ...configured.find(item => item.id === project.id) }
  const feature = copy.zh.features[project.id] || {}
  const featureEn = copy.en.features[project.id] || {}
  return {
    ...value, category: feature.category || '', headline: feature.headline || '', summary: feature.intro || '',
    english: { ...translations.en[project.id], category: featureEn.category || '', headline: featureEn.headline || '', summary: featureEn.intro || '' },
    featured: [1, 3, 5].includes(project.id), sortOrder: order.indexOf(project.id) * 10, status: 'published'
  }
})
fs.writeFileSync(path.join(backend, 'sql/006_project_seed.json'), `${JSON.stringify({ projects }, null, 2)}\n`)
console.log(JSON.stringify({ projects: projects.length, images: projects.reduce((sum, project) => sum + project.images.length, 0) }))
