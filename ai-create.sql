-- 连上 MySQL，直接查
SELECT id, name, prescription_type, manufacturer, approval_number,
       indications, contraindications
FROM drug WHERE id IN (51, 52);

SELECT id, name, prescription_type FROM drug WHERE name LIKE '%布洛芬%';

USE ysd_take_out;

-- 1. 布洛芬
INSERT INTO drug
(name, category_id, price, image, description, status, create_time, update_time,
 create_user, update_user, prescription_type, manufacturer, approval_number, indications, contraindications)
VALUES
    ('布洛芬缓释胶囊', 11, 24.00, NULL, '非甾体抗炎药，解热镇痛', 1, NOW(), NOW(),
     1, 1, 0, '中美天津史克制药有限公司', '国药准字H10900089',
     '用于缓解轻至中度疼痛，如头痛、关节痛、偏头痛、牙痛、肌肉痛、神经痛、痛经；也用于普通感冒或流行性感冒引起的发热。',
     '对其他非甾体抗炎药过敏者禁用；孕妇及哺乳期妇女禁用；对阿司匹林过敏的哮喘患者禁用；活动性消化道溃疡/出血患者禁用。');

-- 2. 阿莫西林
UPDATE drug SET
                prescription_type = 1,
                manufacturer = '石药集团中诺药业(石家庄)有限公司',
                approval_number = '国药准字H13023964',
                indications = '适用于敏感菌所致的上呼吸道感染（中耳炎、鼻窦炎、咽炎、扁桃体炎）、泌尿生殖道感染、皮肤软组织感染及急性支气管炎、肺炎等下呼吸道感染。',
                contraindications = '青霉素过敏及青霉素皮肤试验阳性患者禁用。',
                update_time = NOW()
WHERE id = 51;

-- 3. 头孢克肟
UPDATE drug SET
                prescription_type = 1,
                manufacturer = '成都倍特药业股份有限公司',
                approval_number = '国药准字H20040588',
                indications = '适用于对头孢克肟敏感的链球菌属、肺炎球菌、淋球菌、大肠杆菌、克雷伯杆菌属、变形杆菌属及流感杆菌等引起的支气管炎、肺炎、肾盂肾炎、膀胱炎、胆囊炎、猩红热、中耳炎等感染。',
                contraindications = '对本品及其他头孢菌素类抗生素过敏者禁用。',
                update_time = NOW()
WHERE id = 52;

-- 验证
SELECT id, name, prescription_type, manufacturer FROM drug WHERE id IN (51,52);