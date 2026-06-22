<template>
  <div class="page-container">
    <el-card class="publish-card">
      <template #header>
        <span class="card-title">发布闲置商品</span>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="一句话描述你的宝贝"
            maxlength="50"
            show-word-limit
            clearable
          />
        </el-form-item>

        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 220px">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>

        <el-form-item label="价格" prop="price">
          <el-input-number
            v-model="form.price"
            :min="0"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
          <span class="unit-hint">元</span>
        </el-form-item>

        <el-form-item label="库存" prop="stock">
          <el-input-number
            v-model="form.stock"
            :min="1"
            :precision="0"
            :step="1"
            controls-position="right"
            style="width: 220px"
          />
        </el-form-item>

        <el-form-item label="图片URL" prop="images">
          <el-input
            v-model="form.images"
            type="textarea"
            :rows="3"
            placeholder="图片链接，多张请用英文逗号分隔，如：https://a.jpg,https://b.jpg"
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            placeholder="详细描述商品成色、规格、交易方式等"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSubmit">
            发布
          </el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
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
      validator: (rule, value, callback) => {
        if (value === null || value === undefined) {
          callback(new Error('请输入价格'))
        } else if (Number(value) <= 0) {
          callback(new Error('价格必须大于 0'))
        } else {
          callback()
        }
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
  } catch (e) {
    return
  }
  loading.value = true
  try {
    // images 后端要求逗号分隔的 URL 字符串：清洗多余空格与空项
    const cleanImages = form.images
      .split(',')
      .map((s) => s.trim())
      .filter((s) => !!s)
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
  } catch (e) {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function onReset() {
  formRef.value && formRef.value.resetFields()
  form.price = 0
  form.stock = 1
}
</script>

<style scoped>
.publish-card {
  max-width: 720px;
  margin: 0 auto;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.unit-hint {
  margin-left: 8px;
  color: #909399;
}
</style>
