<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">Publish</h1>
    </div>

    <p class="oa-section-desc">Create a new listing for your campus marketplace.</p>

    <div class="oa-panel publish-panel">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <div class="form-section">
          <label class="oa-form-label">Title</label>
          <el-form-item prop="title">
            <el-input
              v-model="form.title"
              placeholder="Describe your item in one line"
              maxlength="50"
              show-word-limit
              clearable
            />
          </el-form-item>
        </div>

        <div class="form-row">
          <div class="form-section half">
            <label class="oa-form-label">Category</label>
            <el-form-item prop="category">
              <el-select v-model="form.category" placeholder="Select category" style="width: 100%">
                <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
              </el-select>
            </el-form-item>
          </div>
          <div class="form-section half">
            <label class="oa-form-label">Price (CNY)</label>
            <el-form-item prop="price">
              <el-input-number
                v-model="form.price"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </div>
        </div>

        <div class="form-section">
          <label class="oa-form-label">Stock</label>
          <el-form-item prop="stock">
            <el-input-number
              v-model="form.stock"
              :min="1"
              :precision="0"
              :step="1"
              controls-position="right"
              style="width: 200px"
            />
          </el-form-item>
        </div>

        <div class="form-section">
          <label class="oa-form-label">Image URLs</label>
          <el-form-item prop="images">
            <el-input
              v-model="form.images"
              type="textarea"
              :rows="3"
              placeholder="Comma-separated URLs, e.g. https://a.jpg,https://b.jpg"
            />
          </el-form-item>
          <p class="oa-form-hint">Separate multiple image URLs with commas.</p>
        </div>

        <div class="form-section">
          <label class="oa-form-label">Description</label>
          <el-form-item prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="5"
              placeholder="Condition, specs, pickup method, etc."
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </div>

        <hr class="oa-divider" />

        <div class="form-actions">
          <el-button type="primary" :loading="loading" @click="onSubmit">Publish listing</el-button>
          <el-button @click="onReset">Reset</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createProduct } from '@/api/product'
import { CATEGORIES } from '@/constants/product'

const router = useRouter()
const categories = CATEGORIES

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  title: '',
  category: '',
  price: 0,
  stock: 1,
  images: '',
  description: ''
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (value === null || value === undefined) callback(new Error('请输入价格'))
        else if (Number(value) <= 0) callback(new Error('价格必须大于 0'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
  images: [{ required: true, message: '请至少填写一张图片URL', trigger: 'blur' }]
}

async function onSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const cleanImages = form.images
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .join(',')

    await createProduct({
      title: form.title,
      description: form.description,
      price: form.price,
      images: cleanImages,
      category: form.category,
      stock: form.stock
    })
    ElMessage.success('发布成功')
    router.push('/my')
  } finally {
    loading.value = false
  }
}

function onReset() {
  formRef.value?.resetFields()
  form.price = 0
  form.stock = 1
}
</script>

<style scoped>
.publish-panel {
  max-width: 640px;
}

.form-section {
  margin-bottom: 4px;
}

.form-section :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row .half {
  flex: 1;
}

.form-actions {
  display: flex;
  gap: 10px;
}
</style>
